<p align="center"><img src="docs/brand/mobiCode-logo.png" width="170" alt="mobiCode logo"></p>
<h1 align="center">mobiCode</h1>
<p align="center">An on-device coding workspace for Android tablets.</p>
<p align="center"><a href="https://github.com/limuwani/mobiCode/releases">Download Android app</a> · <a href="docs/GETTING_STARTED.md">Get started</a> · <a href="LICENSE">MIT licence</a></p>

mobiCode brings a local code editor, file explorer, real terminals, Git and a
configurable AI agent to your tablet. It was created by Limuwani Nethananni
with the Huawei MatePad 11.5 PaperMatte in mind.

## Install

1. Download **mobiCode.apk** from the newest [0.2 alpha release](https://github.com/limuwani/mobiCode/releases)
   and approve the APK installation when Android asks.
2. Open mobiCode and tap **Download and set up**. The app installs the editor
   and C++, Java, Python and Git tools, then opens your first project.

Use Wi-Fi for the initial download and leave about 2 GB of storage free.
Installed tools work offline. Android arm64 and x86_64 are supported by the
APK. Test builds use development signing. If you have the 0.1 alpha and Android
reports a signature conflict, uninstall that preview before installing 0.2.
[Setup details and troubleshooting](docs/GETTING_STARTED.md)

## Features

| Feature | Implementation |
| --- | --- |
| Editor and extensions | Local code-server workbench with compatible Open VSX and VSIX extensions |
| Terminal and Git | Real shell processes in a private Ubuntu workspace |
| Languages | C++, Java, Python and JavaScript, with runnable starter files |
| Tablet controls | Save, Files, Terminal, Extensions, Agent, task and navigation shortcuts |
| AI | Your own compatible provider, with edit diffs and command review |
| Android files | Document picker for project uploads and downloads |

AI service costs are separate. Local tools work without an AI account.

## Build from source

Use JDK 17, Android SDK 35, Python 3 and Node 22. Run the packaging steps on
Linux or WSL before opening the project in Android Studio.

```sh
python3 tools/native_runtime.py
python3 tools/package.py
python3 tools/verify.py
node --test agent/test/*.test.js
./gradlew :app:assembleDebug :app:lintDebug
```

The APK is at `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions builds
the APK, runs a first-launch test on Android 12 and publishes passing alphas.
See [CONTRIBUTING.md](CONTRIBUTING.md) and [the architecture](docs/ARCHITECTURE.md).

mobiCode source and logo use the [MIT licence](LICENSE). Runtime components
retain their upstream licences. See [third-party notices](THIRD_PARTY_NOTICES.md).
