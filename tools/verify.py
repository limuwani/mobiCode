#!/usr/bin/env python3
"""Validate shipped payloads, resource XML and extension manifests."""
import base64
import io
import json
import pathlib
import tarfile
import xml.etree.ElementTree as ET
import zipfile

root = pathlib.Path(__file__).resolve().parent.parent
for file in (root / 'app/src/main').rglob('*.xml'):
    ET.parse(file)
for file in [root / 'agent/package.json', root / 'runtime/settings.json', root / 'runtime/starter/.vscode/tasks.json']:
    json.loads(file.read_text())
installer = (root / 'app/src/main/assets/mobiCode-setup.sh').read_bytes()
assert installer == (root / 'dist/mobiCode-setup.sh').read_bytes()
payload = installer.split(b'\n__MOBICODE_PAYLOAD__\n', 1)[1]
with tarfile.open(fileobj=io.BytesIO(base64.b64decode(payload)), mode='r:gz') as archive:
    names = archive.getnames()
    assert all(not name.startswith('/') and '..' not in pathlib.PurePosixPath(name).parts for name in names)
    for name in ['mobiCode-agent.vsix', 'starter/main.cpp', 'starter/.vscode/tasks.json', 'start.sh']:
        assert name in names
    data = archive.extractfile('mobiCode-agent.vsix').read()
    assert data == (root / 'dist/mobiCode-agent.vsix').read_bytes()
    with zipfile.ZipFile(io.BytesIO(data)) as vsix:
        ET.fromstring(vsix.read('extension.vsixmanifest'))
        ET.fromstring(vsix.read('[Content_Types].xml'))
        manifest = json.loads(vsix.read('extension/package.json'))
        assert manifest['main'] == './src/extension.js'
        assert 'extension/src/extension.js' in vsix.namelist()
        assert manifest['extensionKind'] == ['workspace']
print('Verified Android XML, extension manifest, embedded installer and starter workspace.')
