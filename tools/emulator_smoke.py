#!/usr/bin/env python3
"""Exercise the same initial setup button a user taps on a clean install."""
import json
import re
import subprocess
import time
import urllib.request
import xml.etree.ElementTree as ET

PACKAGE = 'dev.mobicode.app'


def adb(*args):
    return subprocess.check_output(['adb', *args], text=True, timeout=45).strip()


def find_setup_button():
    for _ in range(10):
        try:
            adb('shell', 'uiautomator', 'dump', '/sdcard/mobicode-setup.xml')
            xml = ET.fromstring(adb('shell', 'cat', '/sdcard/mobicode-setup.xml'))
            for node in xml.iter('node'):
                if node.get('text') == 'Download and set up':
                    left, top, right, bottom = map(int, re.findall(r'\d+', node.get('bounds', '')))
                    return str((left + right) // 2), str((top + bottom) // 2)
        except (subprocess.CalledProcessError, ET.ParseError):
            pass
        time.sleep(2)
    raise RuntimeError('The first-run setup button did not appear.')


def await_workspace():
    last_stage = ''
    for _ in range(120):
        logs = adb('logcat', '-d', '-s', 'mobiCode')
        if 'Workspace startup failed' in logs:
            raise RuntimeError(logs[-18000:])
        ports = re.findall(r'Workspace ready on port (\d+)', logs)
        if ports:
            return ports[-1]
        stage = next((line for line in reversed(logs.splitlines()) if ' I mobiCode:' in line), '')
        if stage != last_stage:
            print(stage, flush=True)
            last_stage = stage
        time.sleep(5)
    raise RuntimeError('Workspace setup did not finish within ten minutes.')


def inspect_editor(port):
    adb('forward', 'tcp:16543', f'tcp:{port}')
    with urllib.request.urlopen('http://127.0.0.1:16543/healthz', timeout=15) as response:
        assert json.load(response)['status'] in ('alive', 'expired')
    pid = adb('shell', 'pidof', PACKAGE).split()[0]
    adb('forward', 'tcp:16544', f'localabstract:webview_devtools_remote_{pid}')
    for _ in range(50):
        try:
            with urllib.request.urlopen('http://127.0.0.1:16544/json/list', timeout=5) as response:
                targets = json.load(response)
            for page in targets:
                if page['url'].startswith(f'http://127.0.0.1:{port}/') and '/login' not in page['url']:
                    title = page.get('title', '')
                    if title and '127.0.0.1' not in title:
                        print(f'Opened signed-in WebView editor: {title}')
                        return
        except (OSError, ValueError):
            pass
        time.sleep(2)
    raise RuntimeError('The editor started, but the WebView did not open the signed-in workspace.')


def main():
    adb('install', '-t', 'dist/mobiCode.apk')
    adb('logcat', '-c')
    adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
    adb('shell', 'input', 'tap', *find_setup_button())
    port = await_workspace()
    inspect_editor(port)
    print('Fresh setup installed the local compilers, started the editor and signed in automatically.')


if __name__ == '__main__':
    try:
        main()
    except Exception:
        print(adb('logcat', '-d', '-s', 'mobiCode', 'AndroidRuntime', 'chromium')[-24000:])
        raise
