#!/data/data/com.termux/files/usr/bin/bash
# mobiCode local engine installer. Generated payload is appended by tools/package.py.
set -euo pipefail
umask 077

if ! command -v pkg >/dev/null 2>&1 || [[ "${PREFIX:-}" != *com.termux/files/usr ]]; then
  echo 'Run this script inside Termux installed directly on the tablet.' >&2
  exit 1
fi

MOBICODE_STATE="$HOME/.local/share/mobicode"
MOBICODE_CONFIG="$HOME/.config/mobicode"
MOBICODE_PROJECT="$HOME/Projects/mobiCode-starter"
MOBICODE_TEMP="$(mktemp -d "$HOME/.mobicode-setup.XXXXXXXX")"
trap 'rm -rf -- "$MOBICODE_TEMP"' EXIT
echo 'mobiCode · installing your local coding engine'
echo 'Internet access is needed for this one-time package download.'

pkg update -y
pkg install -y tur-repo
pkg update -y
pkg install -y code-server git clang python curl

awk '/^__MOBICODE_PAYLOAD__$/{found=1;next} found{print}' "$0" | base64 -d | tar -xz -C "$MOBICODE_TEMP"
mkdir -p "$MOBICODE_STATE/data/User" "$MOBICODE_STATE/extensions" "$MOBICODE_CONFIG" "$MOBICODE_PROJECT"
if [[ ! -f "$MOBICODE_CONFIG/config.yaml" ]]; then
  python - "$MOBICODE_CONFIG/config.yaml" <<'PYCONFIG'
import pathlib, secrets, sys
pathlib.Path(sys.argv[1]).write_text('bind-addr: 127.0.0.1:8765\nauth: password\npassword: ' + secrets.token_urlsafe(24) + '\ncert: false\n')
PYCONFIG
  chmod 600 "$MOBICODE_CONFIG/config.yaml"
fi

if [[ ! -f "$MOBICODE_STATE/data/User/settings.json" ]]; then
  cp "$MOBICODE_TEMP/settings.json" "$MOBICODE_STATE/data/User/settings.json"
fi
# Never replace the user's edited starter project during reinstallation.
if [[ ! -f "$MOBICODE_PROJECT/README.md" ]]; then
  cp -R "$MOBICODE_TEMP/starter/." "$MOBICODE_PROJECT/"
fi
cp "$MOBICODE_TEMP/start.sh" "$MOBICODE_STATE/start.sh"
cp "$MOBICODE_TEMP/install-java.sh" "$MOBICODE_STATE/install-java.sh"
chmod 700 "$MOBICODE_STATE/start.sh" "$MOBICODE_STATE/install-java.sh"

code-server --config "$MOBICODE_CONFIG/config.yaml" \
  --user-data-dir "$MOBICODE_STATE/data" --extensions-dir "$MOBICODE_STATE/extensions" \
  --install-extension "$MOBICODE_TEMP/mobiCode-agent.vsix" --force

echo
echo 'Your workspace is ready: http://127.0.0.1:8765'
echo 'Log in with this local password (keep it private):'
sed -n 's/^password: //p' "$MOBICODE_CONFIG/config.yaml"
echo
echo 'Switch back to mobiCode and tap Workspace.'
echo 'Keep Termux running while you code. Ctrl+C here stops the editor engine.'
echo 'Optional Java tools: bash ~/.local/share/mobicode/install-java.sh'
echo 'Restart later: bash ~/.local/share/mobicode/start.sh'
rm -rf -- "$MOBICODE_TEMP"
trap - EXIT
exec bash "$MOBICODE_STATE/start.sh"
exit 0
