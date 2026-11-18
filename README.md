<p align="center"><img src="docs/brand/mobiCode-logo.png" width="170" alt="mobiCode logo"></p>
<h1 align="center">mobiCode</h1>
<p align="center">Your tablet. Your terminal. Your workspace.</p>
<p align="center"><a href="https://github.com/limuwani/mobiCode/releases">Download Android app</a> · <a href="docs/GETTING_STARTED.md">Get started</a> · <a href="LICENSE">MIT licence</a></p>

mobiCode is an open-source Android tablet client for a local code-server development
environment, with touch shortcuts and a provider-configurable AI agent. Created by
Limuwani Nethananni, initially targeting the Huawei MatePad 11.5 PaperMatte on
HarmonyOS 4.3. Google Play services and GBox are not required by mobiCode.

**Alpha software:** the Android app and Termux integration need on-device testing.
Code-server and Termux are installed separately. This is an initial working
implementation, not a claim that every VS Code desktop feature or extension works
on Android. No Microsoft branding or Marketplace access is bundled.

## Download and install

Open [Releases](https://github.com/limuwani/mobiCode/releases), choose the newest
successful alpha and download **mobiCode.apk**. Releases also include the standalone
Termux installer, AI extension and SHA-256 checksums. If Releases is empty, check
the [Android build](https://github.com/limuwani/mobiCode/actions/workflows/android.yml).

Install the APK directly on the tablet, install [Termux from F-Droid](https://f-droid.org/en/packages/com.termux/),
then follow the app's three setup steps. [Detailed setup →](docs/GETTING_STARTED.md)

## What is implemented

| Feature | Implementation |
| --- | --- |
| Editor, project explorer and tabs | Real code-server workbench hosted locally in Termux |
| Terminals and Git | Real processes and Git in the Termux environment |
| Tablet controls | Native Save, Files, Terminal, Extensions, Agent, task and navigation buttons |
| C++ and Python | Installed by the engine setup; runnable starter projects included |
| Java | Optional OpenJDK 21 installation and starter program |
| Web preview | Local Python server and starter web page |
| Extensions | code-server's compatible extension registry and VSIX installation |
| AI chat | Configurable compatible chat-completions API, endpoint-scoped secret storage |
| Agent tools | Read/list project files, review file diffs, approve and execute commands |
| Android files | System document picker for uploads, exports and local downloads |
| APK downloads | CI compiles, lints and publishes versioned alpha release assets |

Local editing and installed compilers do not require internet. Initial downloads,
Git network operations and remote AI providers do. AI service usage is separate
from this free application. No provider account or key is included.

## Build

Requirements: JDK 17, Android SDK 35 and Python 3. Node 22 is used for agent tests.
Open the root folder in Android Studio, let Gradle sync and build the `app` module.
The bundled installer is committed, so Python is only needed when changing engine
or agent sources.

```sh
python3 tools/package.py
python3 tools/verify.py
node --test agent/test/*.test.js
./gradlew :app:assembleDebug :app:lintDebug
```

On Windows, use `gradlew.bat` and your Python executable. The APK is generated at
`app/build/outputs/apk/debug/app-debug.apk`. Gradle 8.11.1 and Android Gradle Plugin
8.9.2 are pinned; the official Gradle wrapper is included.

## Structure

| Directory | Purpose |
| --- | --- |
| `app/` | Native Android host, onboarding, keyboard controls and document integration |
| `runtime/` | Termux engine scripts, settings and starter workspace |
| `agent/` | Dependency-free VS Code extension and Node tests |
| `tools/` | Reproducible VSIX/installer packaging and payload verification |
| `docs/` | Setup, release status, architecture and brand assets |

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Useful early contributions include testing
touch typing and physical keyboard shortcuts on real tablets, verifying language
extensions and documenting device-specific Termux process restrictions.

## Licence and attribution

The mobiCode source and supplied logo asset are released under the [MIT licence](LICENSE).
Termux, code-server and their dependencies retain their own licences and are
downloaded separately. The Gradle wrapper is Apache-2.0 licensed; see
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
