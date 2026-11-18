package dev.mobicode.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Base64;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native tablet controls around a local, password-protected code-server workspace. */
public final class MainActivity extends Activity {
    private static final String ENGINE = "http://127.0.0.1:8765";
    private static final String TERMUX_PERMISSION = "com.termux.permission.RUN_COMMAND";
    private static final String TERMUX_HOME = "/data/data/com.termux/files/home";
    private static final int CONTROL_PERMISSION = 10, UPLOAD = 11, DOWNLOAD = 12, EXPORT = 13;
    private static final int BG = Color.rgb(16, 21, 29), CARD = Color.rgb(24, 31, 42);
    private static final int FG = Color.rgb(235, 240, 247), MUTED = Color.rgb(153, 168, 187);
    private static final int ACCENT = Color.rgb(56, 216, 190);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private WebView editor;
    private LinearLayout root, shortcuts;
    private FrameLayout content;
    private ScrollView home;
    private TextView status;
    private boolean inEditor, resumed, probing;
    private ValueCallback<Uri[]> uploadCallback;
    private String pendingDownload, pendingCookie, pendingUserAgent, pendingCommand;

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            probeEngine(null);
            handler.postDelayed(this, 5000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setFitsSystemWindows(true);
        setContentView(root);
        buildToolbar();
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        buildHome();
        buildEditor();
        buildShortcuts();
        showHome();
    }

    private void buildToolbar() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(4), dp(10), dp(4));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.brand_logo);
        logo.setContentDescription("mobiCode home");
        row.addView(logo, new LinearLayout.LayoutParams(dp(38), dp(38)));
        logo.setOnClickListener(v -> showHome());
        TextView name = text("mobiCode", 20, FG);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(button("Workspace", v -> openWorkspace()));
        row.addView(button("Menu", v -> showMenu()));
        root.addView(row, new LinearLayout.LayoutParams(-1, dp(62)));
        status = text("Checking local engine…", 12, MUTED);
        status.setPadding(dp(18), 0, dp(18), dp(9));
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        root.addView(status);
    }

    private void buildHome() {
        home = new ScrollView(this);
        home.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24), dp(24), dp(24), dp(32));
        home.addView(page);
        TextView label = text("YOUR WORKSPACE, WITH YOU", 12, ACCENT);
        label.setLetterSpacing(0.12f);
        page.addView(label);
        TextView headline = text("Big ideas.\nAny screen.", 42, FG);
        headline.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        headline.setPadding(0, dp(12), 0, dp(14));
        page.addView(headline);
        page.addView(text("Your projects, a real terminal and an AI agent — on your tablet.\nSet up the local engine once, then code without a laptop.", 16, MUTED));

        LinearLayout first = card(page, "01", "Install Termux", "Install Termux directly on your tablet from its official F-Droid page, then open it once. Install mobiCode directly too; GBox is not needed.");
        first.addView(button("Get Termux", v -> external("https://f-droid.org/en/packages/com.termux/")));
        first.addView(button("Open Termux", v -> openTermux()));

        LinearLayout second = card(page, "02", "Connect your tools", "Allow mobiCode to start the engine through Termux. Copy this small settings command, open Termux and paste it there. This gives mobiCode permission to run commands in your Termux environment.");
        second.addView(button("Copy connection command", v -> {
            copy("Termux connection", controlCommand());
            openTermux();
        }));

        LinearLayout third = card(page, "03", "Build your workspace", "Downloads the editor, Git, a C++ compiler and Python. Keep Termux open while installation runs. Your password appears there. Switch back here afterwards and tap Workspace.");
        third.addView(button("Install engine", v -> installEngine()));
        third.addView(button("Start installed engine", v -> runTermux("bash \"$HOME/.local/share/mobicode/start.sh\"")));
        page.addView(text("FIRST RELEASE · ALPHA\nCompatible extensions vary on Android. AI needs a provider endpoint and model; paid usage is separate. Keep projects in Termux’s home directory so Git and file permissions work correctly.", 12, MUTED));
        content.addView(home, new FrameLayout.LayoutParams(-1, -1));
    }

    private LinearLayout card(LinearLayout page, String number, String title, String description) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(18), dp(20), dp(16));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(CARD);
        shape.setCornerRadius(dp(16));
        shape.setStroke(dp(1), Color.rgb(39, 50, 65));
        card.setBackground(shape);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
        layout.topMargin = dp(20);
        layout.bottomMargin = dp(6);
        page.addView(card, layout);
        card.addView(text(number + "  /  " + title, 19, FG));
        TextView body = text(description, 14, MUTED);
        body.setPadding(0, dp(10), 0, dp(12));
        card.addView(body);
        return card;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void buildEditor() {
        editor = new WebView(this);
        editor.setBackgroundColor(BG);
        WebSettings settings = editor.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true); // Only files selected with Android's document picker.
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false);
        settings.setBuiltInZoomControls(false);
        settings.setTextZoom(100);
        CookieManager.getInstance().setAcceptCookie(true);
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        // Deliberately no JavaScript-to-native bridge is exposed to the editor or extensions.
        editor.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                Uri uri = request.getUrl();
                if (localEngineUrl(uri)) return false;
                if ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) external(uri.toString());
                return true;
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) status.setText("Connection lost · start the engine in Termux, then reload");
            }
            @Override public void onPageFinished(WebView view, String url) {
                CookieManager.getInstance().flush();
                probeEngine(null);
            }
        });
        editor.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (uploadCallback != null) uploadCallback.onReceiveValue(null);
                uploadCallback = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                try { startActivityForResult(intent, UPLOAD); }
                catch (RuntimeException e) { uploadCallback.onReceiveValue(null); uploadCallback = null; toast("No document picker is available."); }
                return true;
            }
        });
        editor.setDownloadListener((url, agent, disposition, mime, length) -> {
            if (!localEngineUrl(Uri.parse(url))) { toast("This download needs an external browser."); return; }
            pendingDownload = url;
            pendingCookie = CookieManager.getInstance().getCookie(url);
            pendingUserAgent = agent;
            Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType(mime == null ? "application/octet-stream" : mime)
                    .putExtra(Intent.EXTRA_TITLE, URLUtil.guessFileName(url, disposition, mime));
            startActivityForResult(save, DOWNLOAD);
        });
        content.addView(editor, new FrameLayout.LayoutParams(-1, -1));
    }

    private void buildShortcuts() {
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        shortcuts = new LinearLayout(this);
        shortcuts.setGravity(Gravity.CENTER_VERTICAL);
        shortcuts.setPadding(dp(6), dp(3), dp(6), dp(3));
        int ctrl = KeyEvent.META_CTRL_ON;
        keyButton("Save", KeyEvent.KEYCODE_S, ctrl);
        keyButton("Files", KeyEvent.KEYCODE_E, ctrl | KeyEvent.META_SHIFT_ON);
        keyButton("Terminal", KeyEvent.KEYCODE_GRAVE, ctrl);
        keyButton("Extensions", KeyEvent.KEYCODE_X, ctrl | KeyEvent.META_SHIFT_ON);
        keyButton("Agent", KeyEvent.KEYCODE_A, ctrl | KeyEvent.META_ALT_ON);
        keyButton("Run task", KeyEvent.KEYCODE_B, ctrl | KeyEvent.META_SHIFT_ON);
        keyButton("Commands", KeyEvent.KEYCODE_P, ctrl | KeyEvent.META_SHIFT_ON);
        keyButton("Esc", KeyEvent.KEYCODE_ESCAPE, 0);
        keyButton("Tab", KeyEvent.KEYCODE_TAB, 0);
        keyButton("←", KeyEvent.KEYCODE_DPAD_LEFT, 0);
        keyButton("→", KeyEvent.KEYCODE_DPAD_RIGHT, 0);
        keyButton("↑", KeyEvent.KEYCODE_DPAD_UP, 0);
        keyButton("↓", KeyEvent.KEYCODE_DPAD_DOWN, 0);
        shortcuts.addView(button("Keyboard", v -> {
            editor.requestFocus();
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
        }));
        scroller.addView(shortcuts);
        root.addView(scroller, new LinearLayout.LayoutParams(-1, dp(58)));
    }

    private void keyButton(String label, int code, int modifiers) {
        shortcuts.addView(button(label, v -> {
            editor.requestFocus();
            long now = SystemClock.uptimeMillis();
            for (int action : new int[] {KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
                editor.dispatchKeyEvent(new KeyEvent(now, SystemClock.uptimeMillis(), action, code, 0, modifiers,
                        -1, 0, KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE, InputDevice.SOURCE_KEYBOARD));
            }
        }));
    }

    private void showHome() {
        inEditor = false;
        home.setVisibility(View.VISIBLE);
        editor.setVisibility(View.GONE);
        ((View) shortcuts.getParent()).setVisibility(View.GONE);
    }

    private void openWorkspace() {
        probeEngine(() -> {
            inEditor = true;
            home.setVisibility(View.GONE);
            editor.setVisibility(View.VISIBLE);
            ((View) shortcuts.getParent()).setVisibility(View.VISIBLE);
            if (editor.getUrl() == null) editor.loadUrl(ENGINE);
            editor.requestFocus();
        });
    }

    private void probeEngine(Runnable whenReady) {
        if (probing && whenReady == null) return;
        probing = true;
        io.execute(() -> {
            boolean ready = false;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(ENGINE + "/healthz").openConnection();
                connection.setConnectTimeout(1400);
                connection.setReadTimeout(1400);
                connection.setInstanceFollowRedirects(false);
                if (connection.getResponseCode() == 200) {
                    try (InputStream stream = connection.getInputStream()) {
                        String body = new String(readBytes(stream, 8192), StandardCharsets.UTF_8);
                        ready = body.contains("\"status\"") && (body.contains("alive") || body.contains("expired"));
                    }
                }
            } catch (Exception ignored) { /* A missing engine is an onboarding state. */ }
            finally { if (connection != null) connection.disconnect(); }
            final boolean available = ready;
            handler.post(() -> {
                probing = false;
                if (isFinishing() || isDestroyed()) return;
                status.setText(available ? "● Local engine connected · 127.0.0.1:8765" : "○ Local engine offline · open Termux to start it");
                status.setTextColor(available ? ACCENT : MUTED);
                if (available && whenReady != null) whenReady.run();
                else if (whenReady != null) new AlertDialog.Builder(this).setTitle("Start your local engine")
                        .setMessage("Complete the setup steps, or choose Start installed engine. The editor runs on this tablet and needs Termux to stay running.")
                        .setPositiveButton("Open Termux", (d, w) -> openTermux()).setNegativeButton("Close", null).show();
            });
        });
    }

    private void installEngine() {
        try {
            String payload = Base64.encodeToString(readBytes(getAssets().open("mobiCode-setup.sh"), 100000), Base64.NO_WRAP);
            runTermux("umask 077; printf %s '" + payload + "' | base64 -d > \"$HOME/.mobicode-install.sh\" && bash \"$HOME/.mobicode-install.sh\"");
        } catch (Exception e) { toast("The bundled installer is missing. Rebuild the app with tools/package.py."); }
    }

    private void runTermux(String command) {
        try { getPackageManager().getPackageInfo("com.termux", 0); }
        catch (PackageManager.NameNotFoundException e) { toast("Install Termux directly and open it once first."); return; }
        pendingCommand = command;
        if (checkSelfPermission(TERMUX_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { TERMUX_PERMISSION }, CONTROL_PERMISSION);
            return;
        }
        executeTermuxCommand();
    }

    private void executeTermuxCommand() {
        if (pendingCommand == null) return;
        Intent run = new Intent("com.termux.RUN_COMMAND");
        run.setClassName("com.termux", "com.termux.app.RunCommandService");
        run.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
        run.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[] { "-c", pendingCommand });
        run.putExtra("com.termux.RUN_COMMAND_WORKDIR", TERMUX_HOME);
        run.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false);
        run.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0");
        try { startService(run); openTermux(); }
        catch (RuntimeException e) { new AlertDialog.Builder(this).setTitle("Termux connection needed")
                .setMessage("Run the connection command in step 2, then grant mobiCode the Run commands in Termux permission in Android app settings.")
                .setPositiveButton("App settings", (d, w) -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))))
                .setNegativeButton("Close", null).show(); }
        pendingCommand = null;
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == CONTROL_PERMISSION && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) executeTermuxCommand();
        else if (request == CONTROL_PERMISSION) { pendingCommand = null; toast("Termux control was not granted. You can export and run the setup script manually from Menu."); }
    }

    private void showMenu() {
        String[] entries = { "Engine setup", "Open workspace in browser", "Copy workspace address", "Export setup script", "Reload editor", "About mobiCode" };
        new AlertDialog.Builder(this).setItems(entries, (d, selected) -> {
            switch (selected) {
                case 0 -> showHome();
                case 1 -> external(ENGINE);
                case 2 -> copy("Workspace", ENGINE);
                case 3 -> startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("text/plain").putExtra(Intent.EXTRA_TITLE, "mobiCode-setup.sh"), EXPORT);
                case 4 -> { if (inEditor) editor.reload(); }
                case 5 -> new AlertDialog.Builder(this).setTitle("mobiCode " + BuildConfig.VERSION_NAME)
                        .setMessage("Open-source tablet coding workspace by Limuwani Nethananni.\n\nPowered by code-server and Termux, installed separately. Not affiliated with Microsoft.\n\nAI is optional and uses your chosen provider. This alpha still needs testing on your tablet.")
                        .setPositiveButton("Close", null).show();
                default -> { }
            }
        }).show();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == UPLOAD && uploadCallback != null) {
            uploadCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
            uploadCallback = null;
        }
        if ((request == EXPORT || request == DOWNLOAD) && result == RESULT_OK && data != null && data.getData() != null) {
            final Uri destination = data.getData();
            final String url = pendingDownload, cookie = pendingCookie, userAgent = pendingUserAgent;
            pendingDownload = pendingCookie = pendingUserAgent = null;
            io.execute(() -> {
                HttpURLConnection connection = null;
                try (OutputStream output = getContentResolver().openOutputStream(destination)) {
                    if (output == null) throw new IllegalStateException("Destination unavailable");
                    if (request == EXPORT) {
                        try (InputStream input = getAssets().open("mobiCode-setup.sh")) { copyStream(input, output); }
                    } else {
                        if (url == null || !localEngineUrl(Uri.parse(url))) throw new IllegalArgumentException("Not a workspace download");
                        connection = (HttpURLConnection) new URL(url).openConnection();
                        connection.setConnectTimeout(10000);
                        connection.setReadTimeout(60000);
                        connection.setInstanceFollowRedirects(false);
                        if (cookie != null) connection.setRequestProperty("Cookie", cookie);
                        if (userAgent != null) connection.setRequestProperty("User-Agent", userAgent);
                        if (connection.getResponseCode() != 200) throw new IllegalStateException("Download failed");
                        try (InputStream input = connection.getInputStream()) { copyStream(input, output); }
                    }
                    handler.post(() -> toast("Saved."));
                } catch (Exception e) { handler.post(() -> toast("Could not save the file. Check the destination and engine connection.")); }
                finally { if (connection != null) connection.disconnect(); }
            });
        } else if (request == DOWNLOAD) pendingDownload = pendingCookie = pendingUserAgent = null;
    }

    private boolean localEngineUrl(Uri uri) {
        return "http".equals(uri.getScheme()) && "127.0.0.1".equals(uri.getHost()) && uri.getPort() == 8765;
    }
    private void external(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (RuntimeException e) { toast("No browser is available to open this link."); }
    }
    private void openTermux() {
        Intent launch = getPackageManager().getLaunchIntentForPackage("com.termux");
        if (launch != null) startActivity(launch); else toast("Install Termux directly on the tablet first.");
    }
    private static String controlCommand() {
        return "mkdir -p \"$HOME/.termux\"\n" +
                "if grep -q '^[[:space:]]*allow-external-apps[[:space:]]*=' \"$HOME/.termux/termux.properties\" 2>/dev/null; then\n" +
                "  sed -i 's/^[[:space:]]*allow-external-apps[[:space:]]*=.*/allow-external-apps = true/' \"$HOME/.termux/termux.properties\"\n" +
                "else\n  printf '\\nallow-external-apps = true\\n' >> \"$HOME/.termux/termux.properties\"\nfi\ntermux-reload-settings\n";
    }
    private void copy(String label, String value) {
        ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText(label, value));
        toast("Copied. Paste it into Termux.");
    }
    private Button button(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(FG);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setFocusable(false);
        button.setMinHeight(dp(46));
        button.setOnClickListener(listener);
        return button;
    }
    private TextView text(String value, float size, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setLineSpacing(dp(3), 1);
        return text;
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
    private static void copyStream(InputStream input, OutputStream output) throws Exception {
        byte[] buffer = new byte[16384];
        int read;
        while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
    }
    private static byte[] readBytes(InputStream input, int limit) throws Exception {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read, total = 0;
            while ((read = stream.read(buffer)) != -1) {
                total += read;
                if (total > limit) throw new IllegalArgumentException("Input too large");
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }
    @Override protected void onResume() { super.onResume(); resumed = true; handler.post(poll); }
    @Override protected void onPause() { resumed = false; handler.removeCallbacks(poll); CookieManager.getInstance().flush(); super.onPause(); }
    @Override public void onBackPressed() { if (inEditor) showHome(); else super.onBackPressed(); }
    @Override protected void onDestroy() {
        resumed = false;
        handler.removeCallbacksAndMessages(null);
        if (uploadCallback != null) uploadCallback.onReceiveValue(null);
        editor.destroy();
        io.shutdownNow();
        super.onDestroy();
    }
}
