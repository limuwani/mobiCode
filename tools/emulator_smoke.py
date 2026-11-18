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
    for _ in range(50):
        if 'Workspace editor rendered' in adb('logcat', '-d', '-s', 'mobiCode'):
            print('GeckoView rendered the signed-in editor.')
            return
        time.sleep(2)
    raise RuntimeError('The local server started, but the signed-in editor did not render.')


def main():
    adb('install', '-t', 'dist/mobiCode-x86_64.apk')
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
        print(adb('logcat', '-d', '-s', 'mobiCode', 'AndroidRuntime', 'GeckoConsole', 'GeckoView')[-24000:])
        raise
