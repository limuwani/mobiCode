# Get started on your MatePad

## 1. Install the app and terminal

Download `mobiCode.apk` from the repository's Releases page. Install it directly
on the tablet. If Huawei asks whether your browser or Files may install this APK,
allow that installation. No Google Play account is needed by mobiCode.

Install Termux directly from https://f-droid.org/en/packages/com.termux/ and open
it once so it can initialize. The two apps must run directly on the same device;
do not install one inside GBox and the other outside it.

## 2. Enable the connection

Open mobiCode. Tap **Copy connection command** in step 2. Paste and run it in
Termux. It enables Termux's `allow-external-apps` option so mobiCode can launch
commands. When mobiCode requests **Run commands in Termux**, grant it. You can
revoke this permission later in Android's app settings.

## 3. Install the coding engine

Tap **Install engine**. Termux downloads code-server, Git, Clang, Python and their
dependencies. Leave it open until setup finishes. The installer prints a random
password for the local editor. Copy that password, switch back to mobiCode, tap
**Workspace**, and enter it on the editor login page.

The service listens only on `127.0.0.1:8765`. Projects are stored under
`~/Projects` in Termux, separately from mobiCode's Android app data. Keep Termux
running while you work. If Huawei stops it in the background, adjust the device's
app-launch/battery settings for Termux using the controls available on your build.

## Run code

The initial workspace contains `main.cpp`, `hello.py`, `Hello.java` and
`index.html`. See its README for commands. Tap **Run task** for the default C++
build, or use **Terminal → Run Task** for the other examples.

Java is optional:

```sh
bash ~/.local/share/mobicode/install-java.sh
```

Restart the engine from mobiCode's home screen, or run:

```sh
bash ~/.local/share/mobicode/start.sh
```

To see the local login password again:

```sh
sed -n 's/^password: //p' ~/.config/mobicode/config.yaml
```

## AI setup

Tap **Agent → Provider**. Enter a compatible chat-completions API base URL, model
ID and optional API key. Your provider's own documentation specifies these values.
No key is shipped in the app. Keys use the extension host's secret storage and
are scoped to the endpoint, so changing endpoints does not forward an old key.

Chat mode answers questions. Agent mode can read/list text files in the first
open project, propose edits through a diff and ask to run a shell command. Save
accepted edits before asking it to compile or execute them. The **Stop** button
cancels the request or running command; changes you already accepted remain.

Conversation and included code/command output go to your chosen provider.
Remote AI generally needs internet and may have a separate usage charge.

## If automatic setup cannot start

Use **Menu → Export setup script** and save `mobiCode-setup.sh` in Downloads.
In Termux, run `termux-setup-storage`, grant storage access and then:

```sh
bash "$HOME/storage/downloads/mobiCode-setup.sh"
```

You can also use the standalone installer from the GitHub release. If the native
WebView has a keyboard or rendering issue, **Menu → Open workspace in browser**
opens the same local editor and files. This does not require another computer.

## Alpha updates

The first alpha APKs use development/debug signing. A later CI build may have a
different development key, so an update can require uninstalling the previous
mobiCode app first. This resets the app's login/session data; projects in Termux
remain. Save your files before doing so. Stable production signing is future work.
