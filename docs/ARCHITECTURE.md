# Architecture

mobiCode is a native Android client for a code-server workbench running locally
inside Termux. It is not a fork of the entire Code-OSS repository. The first
release reuses the editor and extension host so terminals, Git and project files
have real operating-system backing.

| Component | Responsibility |
| --- | --- |
| Android activity | Setup, process-launch requests, native keys, WebView and document picker |
| Termux | User-space environment, compiler packages, Git, Node and shell |
| code-server | Workbench, extension host, terminal PTYs and authentication |
| mobiCode extension | AI provider transport, file tools, diff review and command execution |

The Android app uses Termux's documented `RUN_COMMAND` intent. It never injects
a privileged JavaScript bridge into the editor. HTTP is permitted only for
loopback domains in the Android network policy. The engine binds to loopback
and requires a generated password. External editor links open in the browser.

The AI extension has no npm runtime dependencies. File tools reject traversal,
escaping symlinks, common credential paths, binary files and files over 96 KB.
This is a file-tool boundary, not a sandbox for approved shell commands. A shell
command has the Termux user's permissions and can access that user's files.
Commands require an explicit user decision, have a 30-second timeout and bounded
output, and their process group is stopped on cancellation.

The provider endpoint must use HTTPS, except for localhost model servers. Keys
are stored with VS Code's SecretStorage rather than in settings or webview HTML.
Transport does not follow redirects with credentials. Agent UI renders responses
as text nodes under a restrictive Content Security Policy.

## Upstream references

- https://coder.com/docs/code-server/termux
- https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent
- https://code.visualstudio.com/api/extension-guides/webview
- https://code.visualstudio.com/api/references/vscode-api
- https://code.visualstudio.com/docs/supporting/faq

## Next engineering work

Test the APK and IME behaviour on the target tablet; add integration coverage for
VS Code diff/apply flows; establish stable production signing; improve native
touch editing and split panes; evaluate a Linux userspace option for extensions
that cannot run directly in Termux. Do not advertise universal extension support.
