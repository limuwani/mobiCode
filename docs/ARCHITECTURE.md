# Architecture

mobiCode creates a private Ubuntu workspace within its Android app storage.
The APK contains the PRoot launcher. First launch downloads Ubuntu Base and
the code-server archive from their upstream sources and checks their pinned
SHA-256 hashes. The Ubuntu package manager verifies its signed package
metadata and installs the language tools inside the workspace.

| Component | Role |
| --- | --- |
| Android activity | Setup, WebView editor, touch shortcuts and file picker |
| Workspace service | Resumable setup, progress, editor lifecycle and automatic local sign-in |
| PRoot and Ubuntu | Local shell, compilers, Git and Linux extension host |
| code-server | Project workbench, terminal PTYs and extension installation |
| mobiCode agent | Provider requests, project file tools and review of edits and commands |

Archives are expanded in a staging directory and moved into place after
extraction. Files and directories are checked before writing, and extraction
does not follow symlinks. Installation uses a private workspace directory and
keeps verified stages on retry. Ubuntu's package database remains available
for future updates.

The editor binds only to Android loopback on a randomly chosen port. A new
password protects each session and the Android app signs in using the local
login endpoint, then passes the session cookie to the WebView. The editor has
no native JavaScript bridge.

Agent file tools are limited to the open project and reject common credential
paths. Reviewed shell commands run with the workspace's permissions. Provider
keys use the editor's secret storage and are scoped to their endpoint.

- [Ubuntu Base](https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/)
- [code-server standalone builds](https://coder.com/docs/code-server/install#standalone-releases)
- [PRoot for Android](https://github.com/termux/proot)
