# Start coding with mobiCode

1. Download **mobiCode.apk** from [Releases](https://github.com/limuwani/mobiCode/releases).
   Open the file on your tablet and approve the Android installation prompt.
2. Open mobiCode and tap **Download and set up**. Use Wi-Fi and keep about 2 GB
   of storage free. The editor opens automatically when setup finishes.

The app installs the local workspace, editor, C++, Java, Python and Git. It
starts the editor again when you next open mobiCode. You can pause setup or
tap **Try again** after a connection failure. Verified downloads and completed
installation stages are reused.

## Run your first project

Tap **Run task** to compile and run the bundled C++ example. Use **Terminal →
Run Task** to run Python, Java or the web preview. Create or clone projects in
`~/Projects` and open them with **File → Open Folder**. The workspace belongs to
mobiCode. Export or push projects to Git before uninstalling the app.

## Configure AI

Tap **Agent → Provider** and enter your provider's compatible chat-completions
base URL, model ID and optional API key. Agent mode needs a model that supports
tool calls. Included code and command output are sent to your provider.

The agent shows a diff before applying an edit and asks before running a
command in the workspace. Save edits before running them. A remote provider
can charge for usage.

## Updating from the original preview

The alpha builds use development signing. If Android reports that the earlier
APK has a different signature, uninstall mobiCode 0.1 and install this APK.
Projects created in the older Termux setup live in Termux. Export those files
and import them through the editor to use them in the new workspace.
