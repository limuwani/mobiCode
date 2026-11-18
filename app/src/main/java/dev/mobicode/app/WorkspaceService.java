package dev.mobicode.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Base64;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

public final class WorkspaceService extends Service {
    static final String STOP = "dev.mobicode.app.STOP_WORKSPACE";
    private static final String CHANNEL = "workspace";
    private static final int NOTIFICATION = 471;
    private static final String TAG = "mobiCode";
    private static final ReentrantLock INSTALL_LOCK = new ReentrantLock();

    enum Phase { IDLE, DOWNLOADING, INSTALLING, STARTING, READY, FAILED }

    static final class State {
        final Phase phase;
        final String title, detail, url, cookie;
        final int percent;

        State(Phase phase, String title, String detail, int percent, String url, String cookie) {
            this.phase = phase;
            this.title = title;
            this.detail = detail;
            this.percent = percent;
            this.url = url;
            this.cookie = cookie;
        }

        boolean busy() { return phase == Phase.DOWNLOADING || phase == Phase.INSTALLING || phase == Phase.STARTING; }
    }

    private static final State IDLE = new State(Phase.IDLE, "", "", -1, null, null);
    private static volatile State state = IDLE;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile Process editor;
    private volatile boolean stopping;
    private long lastNotification;

    static State state() { return state; }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (intent != null && STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (state.busy() || state.phase == Phase.READY) return START_NOT_STICKY;
        state = new State(Phase.STARTING, "Starting workspace", "", -1, null, null);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "mobiCode workspace", NotificationManager.IMPORTANCE_LOW));
        Notification first = notification(state);
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION, first, 0);
        } else {
            startForeground(NOTIFICATION, first);
        }
        worker.execute(this::startWorkspace);
        return START_NOT_STICKY;
    }

    private void startWorkspace() {
        try {
            LinuxWorkspace workspace = new LinuxWorkspace(this, this);
            INSTALL_LOCK.lockInterruptibly();
            try { workspace.install(); }
            finally { INSTALL_LOCK.unlock(); }
            WorkspaceFiles.checkRunning();
            publish(new State(Phase.STARTING, "Opening your workspace", "", -1, null, null));
            int port;
            try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
                port = socket.getLocalPort();
            }
            String url = "http://127.0.0.1:" + port;
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            String password = Base64.encodeToString(random, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            Process process = workspace.startEditor(port, password);
            editor = process;
            Thread log = new Thread(() -> drainEditorLog(process), "mobiCode editor log");
            log.setDaemon(true);
            log.start();
            String cookie = waitForEditor(process, url, password);
            publish(new State(Phase.READY, "Workspace ready", "", -1, url, cookie));
            if (BuildConfig.DEBUG) Log.i(TAG, "Workspace ready on port " + port);
            int code = process.waitFor();
            if (!stopping) throw new IOException("The editor closed (" + code + "). Tap Try again to reopen it.");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Exception error) {
            if (!stopping) {
                String message = error.getLocalizedMessage();
                if (error instanceof java.net.UnknownHostException || error instanceof java.net.SocketTimeoutException) {
                    message = "Check your connection and tap Try again. Downloaded files will be reused.";
                }
                if (message == null) message = "Tap Try again to continue setup.";
                if (BuildConfig.DEBUG) Log.e(TAG, "Workspace startup failed", error);
                publish(new State(Phase.FAILED, "Couldn’t open workspace", message, -1, null, null));
            }
        } finally {
            Process process = editor;
            if (process != null && process.isAlive()) process.destroyForcibly();
            stopSelf();
        }
    }

    private void drainEditorLog(Process process) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null;) {
                if (BuildConfig.DEBUG && !line.toLowerCase(java.util.Locale.ROOT).contains("password")) Log.d(TAG, line);
            }
        } catch (IOException ignored) { }
    }

    private static String waitForEditor(Process process, String baseUrl, String password) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 120000;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (!process.isAlive()) throw new IOException("The local editor stopped during startup. Tap Try again.");
            if (isHealthy(baseUrl)) return signIn(baseUrl, password);
            Thread.sleep(500);
        }
        throw new IOException("The editor took too long to open. Tap Try again.");
    }

    private static boolean isHealthy(String url) {
        HttpURLConnection connection = null;
        try {
            connection = connection(url + "/healthz");
            return connection.getResponseCode() == 200;
        } catch (IOException ignored) { return false; }
        finally { if (connection != null) connection.disconnect(); }
    }

    private static String signIn(String url, String password) throws IOException {
        HttpURLConnection connection = connection(url + "/login");
        try {
            byte[] form = ("password=" + password + "&base=%2F").getBytes(StandardCharsets.UTF_8);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setFixedLengthStreamingMode(form.length);
            try (OutputStream output = connection.getOutputStream()) { output.write(form); }
            int result = connection.getResponseCode();
            String cookie = connection.getHeaderField("Set-Cookie");
            if (result < 300 || result >= 400 || cookie == null) throw new IOException("Could not connect to the local editor session.");
            return cookie;
        } finally { connection.disconnect(); }
    }

    private static HttpURLConnection connection(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection(java.net.Proxy.NO_PROXY);
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(1500);
        connection.setReadTimeout(2000);
        return connection;
    }

    void download(String title, Path destination, WorkspaceFiles.Download source) throws Exception {
        WorkspaceFiles.download(source, destination, saved -> publish(new State(Phase.DOWNLOADING, title,
                (saved / WorkspaceFiles.MIB) + " of " + ((source.bytes + WorkspaceFiles.MIB - 1) / WorkspaceFiles.MIB) + " MB",
                (int) (100 * saved / source.bytes), null, null)));
    }

    void step(String title, String detail) {
        publish(new State(Phase.INSTALLING, title, detail, -1, null, null));
        if (BuildConfig.DEBUG) Log.i(TAG, title);
    }

    void detail(String line) {
        State current = state;
        if (current.phase != Phase.INSTALLING) return;
        publish(new State(Phase.INSTALLING, current.title, line, -1, null, null));
        if (BuildConfig.DEBUG) Log.d(TAG, line);
    }

    private synchronized void publish(State update) {
        if (stopping) return;
        State previous = state;
        state = update;
        long now = SystemClock.elapsedRealtime();
        if (previous.phase != update.phase || now - lastNotification >= 1000) {
            lastNotification = now;
            getSystemService(NotificationManager.class).notify(NOTIFICATION, notification(update));
        }
    }

    private Notification notification(State update) {
        PendingIntent open = PendingIntent.getActivity(this, 1, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 2, new Intent(this, WorkspaceService.class).setAction(STOP), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("mobiCode").setContentText(update.title).setOnlyAlertOnce(true).setOngoing(true)
                .setContentIntent(open).setProgress(100, Math.max(0, update.percent), update.busy() && update.percent < 0)
                .addAction(new Notification.Action.Builder(null, "Stop", stop).build()).build();
    }

    @Override public void onDestroy() {
        stopping = true;
        worker.shutdownNow();
        Process process = editor;
        if (process != null && process.isAlive()) process.destroyForcibly();
        if (state.phase != Phase.FAILED) state = IDLE;
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
}
