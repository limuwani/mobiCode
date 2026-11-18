This is the first mobiCode Android development preview.

Download **mobiCode.apk**, install it directly on the tablet, and follow the three
setup steps. Install Termux from its official F-Droid page first. A standalone
**mobiCode-setup.sh** installer and **mobiCode-agent.vsix** are also included.

Implemented: local code-server editor, native tablet shortcuts, real terminals,
Git, C++ and Python setup, optional Java, web preview, extension access, and a
provider-configurable AI chat/agent with file diffs and reviewed shell commands.

The APK is built and linted in GitHub Actions; agent tests and payload checks run
before publication. This does not replace testing on a physical tablet.

Known limits:

- First target: Huawei MatePad 11.5 PaperMatte with HarmonyOS 4.3; device validation
  is still needed. Touch typing and keyboard shortcuts need real-device checks.
- Termux is a separate dependency and must remain running for local execution.
- Some VS Code extensions and native dependencies are incompatible with Android.
- AI requires your own compatible provider/model and optionally an API key.
- Debug/development signing is used for alpha APKs. Reinstallation may be needed
  between builds; save files first. Stable release signing is not configured yet.

Check **SHA256SUMS** to verify the downloaded assets.
