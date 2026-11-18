package dev.mobicode.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.URLUtil;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebRequestError;
import org.mozilla.geckoview.WebResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native tablet controls around a local, password-protected code-server workspace. */
public final class MainActivity extends Activity {
    private static final int UPLOAD = 11, DOWNLOAD = 12;
    private static final int BG = Color.rgb(16, 21, 29), CARD = Color.rgb(24, 31, 42);
    private static final int FG = Color.rgb(235, 240, 247), MUTED = Color.rgb(153, 168, 187);
    private static final int ACCENT = Color.rgb(56, 216, 190);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private static GeckoRuntime browserRuntime;
    private GeckoView editor;
    private GeckoSession editorSession;
    private LinearLayout root, shortcuts;
    private FrameLayout content;
    private ScrollView home;
    private TextView status, setupTitle, setupDetail;
    private Button setupButton, stopSetup, workspaceButton;
    private ProgressBar progress;
    private boolean inEditor, resumed, autoOpen = true, autoStarted;
    private String activeUrl;
    private InputStream pendingDownload;
    private GeckoSession.PromptDelegate.FilePrompt filePrompt;
    private GeckoResult<GeckoSession.PromptDelegate.PromptResponse> filePicker;
    private WorkspaceService.State observed;

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            renderWorkspaceState();
            handler.postDelayed(this, 450);
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
        autoOpen = true;
        observed = null;
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
        workspaceButton = button("Workspace", v -> requestWorkspace());
        row.addView(workspaceButton);
        row.addView(button("Menu", v -> showMenu()));
        root.addView(row, new LinearLayout.LayoutParams(-1, dp(62)));
        status = text("On-device workspace", 12, MUTED);
        status.setPadding(dp(18), 0, dp(18), dp(9));
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        root.addView(status);
    }

    private void buildHome() {
        home = new ScrollView(this);
        home.setFillViewport(true);
        FrameLayout page = new FrameLayout(this);
        page.setPadding(dp(24), dp(28), dp(24), dp(28));
        home.addView(page, new ScrollView.LayoutParams(-1, -2));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(26), dp(24), dp(26), dp(22));
        GradientDrawable background = new GradientDrawable();
        background.setColor(CARD);
        background.setCornerRadius(dp(22));
        background.setStroke(dp(1), Color.rgb(48, 60, 78));
        card.setBackground(background);
        page.addView(card, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        page.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int width = Math.min(dp(520), right - left - dp(48));
            if (width > 0 && card.getLayoutParams().width != width) {
                card.getLayoutParams().width = width;
                card.requestLayout();
            }
        });

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.brand_logo);
        card.addView(logo, new LinearLayout.LayoutParams(dp(68), dp(68)));
        setupTitle = text("Set up your workspace", 29, FG);
        setupTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        setupTitle.setPadding(0, dp(4), 0, dp(10));
        card.addView(setupTitle);
        setupDetail = text("", 15, MUTED);
        setupDetail.setPadding(0, 0, 0, dp(14));
        setupDetail.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        card.addView(setupDetail);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setIndeterminateTintList(ColorStateList.valueOf(ACCENT));
        progress.setProgressTintList(ColorStateList.valueOf(ACCENT));
        progress.setContentDescription("Workspace setup progress");
        card.addView(progress, new LinearLayout.LayoutParams(-1, dp(12)));
        setupButton = button("Download and set up", v -> requestWorkspace());
        setupButton.setTextColor(BG);
        setupButton.setTextSize(15);
        setupButton.setBackgroundTintList(ColorStateList.valueOf(ACCENT));
        LinearLayout.LayoutParams action = new LinearLayout.LayoutParams(-1, dp(54));
        action.topMargin = dp(12);
        card.addView(setupButton, action);
        stopSetup = button("Pause setup", v -> stopWorkspace());
        card.addView(stopSetup, new LinearLayout.LayoutParams(-1, dp(48)));
        content.addView(home, new FrameLayout.LayoutParams(-1, -1));
        renderWorkspaceState();
    }

    private void buildEditor() {
        editor = new GeckoView(this);
        editor.setBackgroundColor(BG);
        content.addView(editor, new FrameLayout.LayoutParams(-1, -1));
    }

    private void openEditorSession() {
        if (editorSession != null) return;
        if (browserRuntime == null) browserRuntime = GeckoRuntime.create(getApplicationContext());
        editorSession = new GeckoSession();
        editorSession.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override public GeckoResult<AllowOrDeny> onLoadRequest(GeckoSession session, LoadRequest request) {
                Uri uri = Uri.parse(request.uri);
                if (localEngineUrl(uri) || ("data".equals(uri.getScheme()) && request.isDirectNavigation)) {
                    return GeckoResult.fromValue(AllowOrDeny.ALLOW);
                }
                if (request.hasUserGesture && ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))) {
                    external(request.uri);
                }
                return GeckoResult.fromValue(AllowOrDeny.DENY);
            }

            @Override public GeckoResult<String> onLoadError(GeckoSession session, String url, WebRequestError error) {
                status.setText("Connection lost. Use Menu to reload the editor.");
                if (BuildConfig.DEBUG) Log.w("mobiCode", "Editor page load failed: " + error.code);
                return null;
            }
        });
        editorSession.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override public void onTitleChange(GeckoSession session, String title) {
                if (BuildConfig.DEBUG && title != null && title.contains("mobiCode")) {
                    Log.i("mobiCode", "Workspace editor rendered");
                }
            }

            @Override public void onExternalResponse(GeckoSession session, WebResponse download) {
                Uri origin = Uri.parse(download.uri);
                if (download.body == null) return;
                if (!localEngineUrl(origin) && (activeUrl == null || !download.uri.startsWith("blob:" + activeUrl + "/"))) {
                    closeStream(download.body);
                    return;
                }
                closeStream(pendingDownload);
                pendingDownload = download.body;
                String mimeType = download.headers.get("Content-Type");
                try {
                    startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE)
                            .setType(mimeType == null ? "application/octet-stream" : mimeType.split(";", 2)[0])
                            .putExtra(Intent.EXTRA_TITLE, URLUtil.guessFileName(download.uri, download.headers.get("Content-Disposition"), mimeType)), DOWNLOAD);
                } catch (RuntimeException error) {
                    closeStream(pendingDownload);
                    pendingDownload = null;
                    toast("Couldn’t open the file picker.");
                }
            }
        });
        editorSession.setPromptDelegate(new GeckoSession.PromptDelegate() {
            @Override public GeckoResult<PromptResponse> onFilePrompt(GeckoSession session, FilePrompt prompt) {
                if (filePicker != null) filePicker.complete(filePrompt.dismiss());
                filePrompt = prompt;
                GeckoResult<PromptResponse> response = new GeckoResult<>();
                filePicker = response;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                        .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, prompt.type == FilePrompt.Type.MULTIPLE);
                if (prompt.mimeTypes != null) intent.putExtra(Intent.EXTRA_MIME_TYPES, prompt.mimeTypes);
                try { startActivityForResult(intent, UPLOAD); }
                catch (RuntimeException error) {
                    response.complete(prompt.dismiss());
                    filePicker = null;
                    filePrompt = null;
                    toast("Couldn’t open the file picker.");
                }
                return response;
            }
        });
        editorSession.open(browserRuntime);
        editor.setSession(editorSession);
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
        autoOpen = false;
        inEditor = false;
        home.setVisibility(View.VISIBLE);
        editor.setVisibility(View.GONE);
        ((View) shortcuts.getParent()).setVisibility(View.GONE);
    }

    private void requestWorkspace() {
        autoOpen = true;
        WorkspaceService.State current = WorkspaceService.state();
        if (current.phase == WorkspaceService.Phase.READY) {
            showWorkspace(current);
        } else if (!current.busy()) {
            setupButton.setEnabled(false);
            try { startForegroundService(new Intent(this, WorkspaceService.class)); }
            catch (RuntimeException error) {
                setupButton.setEnabled(true);
                setupDetail.setText("Couldn’t start the workspace. Tap again to retry.");
            }
        }
    }

    private void renderWorkspaceState() {
        WorkspaceService.State current = WorkspaceService.state();
        if (current == observed) return;
        observed = current;
        if (current.phase == WorkspaceService.Phase.READY) {
            status.setText("Workspace ready");
            status.setTextColor(ACCENT);
            setupTitle.setText("Workspace ready");
            setupDetail.setText("Your editor and tools are ready to use.");
            setupButton.setText("Open workspace");
            if (autoOpen && editor != null && !inEditor) showWorkspace(current);
        } else {
            if (inEditor) showHome();
            activeUrl = null;
            status.setText("On-device workspace");
            status.setTextColor(MUTED);
            if (current.phase == WorkspaceService.Phase.IDLE) {
                boolean installed = LinuxWorkspace.isInstalled(this);
                setupTitle.setText(installed ? "Open your workspace" : "Set up your workspace");
                setupDetail.setText(installed ? "Your editor and coding tools are installed." :
                        "Download the editor, terminal, Git, C++, Java and Python in one setup. Use Wi‑Fi for several hundred MB of downloads and keep about 2 GB free. Once installed, you can code offline.");
                setupButton.setText(installed ? "Open workspace" : "Download and set up");
            } else {
                setupTitle.setText(current.title);
                setupDetail.setText(current.detail.isEmpty() ? "mobiCode will open the editor when setup finishes." : current.detail);
                setupButton.setText("Try again");
            }
        }
        setupButton.setEnabled(!current.busy());
        workspaceButton.setVisibility(current.phase == WorkspaceService.Phase.READY ? View.VISIBLE : View.GONE);
        setupButton.setVisibility(current.busy() ? View.GONE : View.VISIBLE);
        stopSetup.setVisibility(current.busy() ? View.VISIBLE : View.GONE);
        progress.setVisibility(current.busy() ? View.VISIBLE : View.GONE);
        progress.setIndeterminate(current.percent < 0);
        if (current.percent >= 0) progress.setProgress(current.percent);
    }

    private void showWorkspace(WorkspaceService.State session) {
        if (isFinishing() || isDestroyed()) return;
        inEditor = true;
        home.setVisibility(View.GONE);
        editor.setVisibility(View.VISIBLE);
        ((View) shortcuts.getParent()).setVisibility(View.VISIBLE);
        if (!session.url.equals(activeUrl)) {
            openEditorSession();
            activeUrl = session.url;
            // Open the authenticated session through the renderer's own cookie store.
            String form = "<!doctype html><meta charset=utf-8>"
                    + "<meta http-equiv='Content-Security-Policy' content=\"default-src 'none'; script-src 'unsafe-inline'; form-action " + session.url + "\">"
                    + "<form id='login' method='post' action='" + session.url + "/login'>"
                    + "<input type='hidden' name='password' value='" + session.password + "'>"
                    + "<input type='hidden' name='base' value='/'>"
                    + "</form><script>document.getElementById('login').submit()</script>";
            editorSession.load(new GeckoSession.Loader().data(form, "text/html").flags(GeckoSession.LOAD_FLAGS_REPLACE_HISTORY));
        }
    }

    private void stopWorkspace() {
        autoOpen = false;
        stopService(new Intent(this, WorkspaceService.class));
        showHome();
    }

    private void showMenu() {
        String[] entries = { "Setup and status", "Reload editor", "Stop workspace", "About mobiCode" };
        new AlertDialog.Builder(this).setItems(entries, (d, selected) -> {
            switch (selected) {
                case 0 -> showHome();
                case 1 -> {
                    if (inEditor && editorSession != null) editorSession.reload();
                    else if (LinuxWorkspace.isInstalled(this)) requestWorkspace();
                    else showHome();
                }
                case 2 -> stopWorkspace();
                case 3 -> new AlertDialog.Builder(this).setTitle("mobiCode " + BuildConfig.VERSION_NAME)
                        .setMessage("Open-source Android coding workspace by Limuwani Nethananni.\n\nThe editor and language tools run on your device. AI is optional and uses your chosen provider.")
                        .setPositiveButton("Close", null).show();
                default -> { }
            }
        }).show();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == UPLOAD && filePicker != null) {
            if (result != RESULT_OK || data == null) {
                filePicker.complete(filePrompt.dismiss());
            } else if (data.getClipData() != null) {
                android.content.ClipData selection = data.getClipData();
                Uri[] files = new Uri[selection.getItemCount()];
                for (int i = 0; i < files.length; i++) files[i] = selection.getItemAt(i).getUri();
                filePicker.complete(filePrompt.confirm(this, files));
            } else if (data.getData() != null) {
                filePicker.complete(filePrompt.confirm(this, data.getData()));
            } else {
                filePicker.complete(filePrompt.dismiss());
            }
            filePicker = null;
            filePrompt = null;
        }
        if (request == DOWNLOAD && pendingDownload != null) {
            InputStream download = pendingDownload;
            pendingDownload = null;
            if (result != RESULT_OK || data == null || data.getData() == null) {
                closeStream(download);
                return;
            }
            Uri destination = data.getData();
            io.execute(() -> {
                try (InputStream input = download; OutputStream output = getContentResolver().openOutputStream(destination)) {
                    if (output == null) throw new IOException("Destination unavailable");
                    copyStream(input, output);
                    handler.post(() -> toast("Saved."));
                } catch (IOException error) {
                    handler.post(() -> toast("Couldn’t save this file."));
                }
            });
        }
    }

    private static void closeStream(InputStream stream) {
        if (stream != null) {
            try { stream.close(); } catch (IOException ignored) { }
        }
    }

    private boolean localEngineUrl(Uri uri) {
        WorkspaceService.State current = WorkspaceService.state();
        return current.phase == WorkspaceService.Phase.READY && "http".equals(uri.getScheme())
                && "127.0.0.1".equals(uri.getHost()) && uri.getPort() == Uri.parse(current.url).getPort();
    }
    private void external(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (RuntimeException e) { toast("No browser is available to open this link."); }
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
    private static void copyStream(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[16384];
        int read;
        while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
    }
    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        handler.post(poll);
        if (!autoStarted && LinuxWorkspace.isInstalled(this) && WorkspaceService.state().phase == WorkspaceService.Phase.IDLE) {
            autoStarted = true;
            requestWorkspace();
        }
    }
    @Override protected void onPause() { resumed = false; handler.removeCallbacks(poll); super.onPause(); }
    @Override public void onBackPressed() { if (inEditor) showHome(); else super.onBackPressed(); }
    @Override protected void onDestroy() {
        resumed = false;
        handler.removeCallbacksAndMessages(null);
        if (filePicker != null) filePicker.complete(filePrompt.dismiss());
        if (editorSession != null) {
            editor.releaseSession();
            editorSession.close();
        }
        closeStream(pendingDownload);
        io.shutdownNow();
        super.onDestroy();
    }
}
