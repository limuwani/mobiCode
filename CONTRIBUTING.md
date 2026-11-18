# Contributing to mobiCode

Use JDK 17, Android SDK 37.2, Node 22 and Python 3. The project has no npm runtime
dependencies. Follow the build commands in the README.

When editing `agent/` or `runtime/`, run `python3 tools/package.py` to regenerate
the bundled asset. Run the Node tests and `python3 tools/verify.py`, then build
and lint the Android module. Changes to first launch should also pass the
Android emulator setup test in the Actions workflow.

For device reports, include your tablet model, HarmonyOS/Android version, WebView
version, input method and whether a physical keyboard was connected. Never include
API keys, local editor passwords or unrelated personal files in a public report.

Keep native UI controls connected to actual editor operations. Label unimplemented
features and unverified hardware support clearly. Do not replace real terminals
with simulated output or claim an AI provider succeeded when it returned an error.

Changes to shell execution, credentials, workspace paths or file edits need
meaningful regression coverage. Use small, focused pull requests and document any
new runtime dependency and its licence.
