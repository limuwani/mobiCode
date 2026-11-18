#!/usr/bin/env python3
"""Validate shipped payloads, resource XML and extension manifests."""
import io
import json
import pathlib
import sys
import tarfile
import xml.etree.ElementTree as ET
import zipfile

root = pathlib.Path(__file__).resolve().parent.parent
for file in (root / 'app/src/main').rglob('*.xml'):
    ET.parse(file)
for file in [root / 'agent/package.json', root / 'runtime/settings.json', root / 'runtime/starter/.vscode/tasks.json']:
    json.loads(file.read_text())
starter = root / 'app/src/main/res/raw/workspace_starter.bin'
with tarfile.open(starter, mode='r:gz') as archive:
    names = archive.getnames()
    assert all(not name.startswith('/') and '..' not in pathlib.PurePosixPath(name).parts for name in names)
    for name in ['.local/share/mobicode/mobiCode-agent.vsix', 'Projects/mobiCode-starter/main.cpp', 'Projects/mobiCode-starter/.vscode/tasks.json']:
        assert name in names
    data = archive.extractfile('.local/share/mobicode/mobiCode-agent.vsix').read()
    assert data == (root / 'dist/mobiCode-agent.vsix').read_bytes()
    with zipfile.ZipFile(io.BytesIO(data)) as vsix:
        ET.fromstring(vsix.read('extension.vsixmanifest'))
        ET.fromstring(vsix.read('[Content_Types].xml'))
        manifest = json.loads(vsix.read('extension/package.json'))
        assert manifest['main'] == './src/extension.js'
        assert 'extension/src/extension.js' in vsix.namelist()
        assert manifest['extensionKind'] == ['workspace']
if len(sys.argv) == 2:
    with zipfile.ZipFile(sys.argv[1]) as apk:
        assert apk.read('res/raw/workspace_starter.bin') == starter.read_bytes(), 'Starter project payload missing from APK'
print('Verified Android XML, extension manifest and embedded starter workspace.')
