#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
MOBICODE_STATE="$HOME/.local/share/mobicode"
MOBICODE_CONFIG="$HOME/.config/mobicode/config.yaml"
if [[ ! -f "$MOBICODE_CONFIG" ]]; then
  echo 'Run the mobiCode installer first.' >&2
  exit 1
fi
if curl -fsS --max-time 2 http://127.0.0.1:8765/healthz 2>/dev/null | grep -q '"status"'; then
  echo 'The engine is already running. Open mobiCode → Workspace.'
  exit 0
fi
echo 'mobiCode · local workspace at http://127.0.0.1:8765'
echo 'Your password is in ~/.config/mobicode/config.yaml'
exec code-server --config "$MOBICODE_CONFIG" --bind-addr 127.0.0.1:8765 --auth password \
  --user-data-dir "$MOBICODE_STATE/data" --extensions-dir "$MOBICODE_STATE/extensions" \
  --disable-telemetry --disable-update-check "$HOME/Projects/mobiCode-starter"
