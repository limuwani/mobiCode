#!/usr/bin/env python3
"""Build a reproducible VSIX and self-contained Termux installer, without npm dependencies."""
import base64
import gzip
import io
import json
import pathlib
import tarfile
import textwrap
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
DIST = ROOT / 'dist'


def zip_entry(archive, name, contents):
    info = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
    info.compress_type = zipfile.ZIP_DEFLATED
    info.external_attr = 0o100644 << 16
    archive.writestr(info, contents)


def package():
    DIST.mkdir(exist_ok=True)
    manifest = json.loads((ROOT / 'agent/package.json').read_text())
    vsix_bytes = io.BytesIO()
    with zipfile.ZipFile(vsix_bytes, 'w') as archive:
        zip_entry(archive, '[Content_Types].xml', '<?xml version="1.0"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="json" ContentType="application/json"/><Default Extension="js" ContentType="application/javascript"/><Default Extension="svg" ContentType="image/svg+xml"/><Default Extension="md" ContentType="text/markdown"/><Default Extension="vsixmanifest" ContentType="text/xml"/></Types>')
        xml = f'''<?xml version="1.0" encoding="utf-8"?>
<PackageManifest Version="2.0.0" xmlns="http://schemas.microsoft.com/developer/vsx-schema/2011">
<Metadata><Identity Language="en-US" Id="{manifest['name']}" Version="{manifest['version']}" Publisher="{manifest['publisher']}"/>
<DisplayName>mobiCode Agent</DisplayName><Description xml:space="preserve">A coding agent for mobiCode.</Description><Tags>agent</Tags><Categories>Other</Categories><GalleryFlags>Public</GalleryFlags>
<Properties><Property Id="Microsoft.VisualStudio.Code.Engine" Value="^1.85.0"/><Property Id="Microsoft.VisualStudio.Code.ExtensionKind" Value="workspace"/></Properties><License>extension/LICENSE</License></Metadata>
<Installation><InstallationTarget Id="Microsoft.VisualStudio.Code"/></Installation><Dependencies/>
<Assets><Asset Type="Microsoft.VisualStudio.Code.Manifest" Path="extension/package.json" Addressable="true"/></Assets></PackageManifest>'''
        zip_entry(archive, 'extension.vsixmanifest', xml)
        for relative in ['package.json', 'media/agent.svg'] + [str(file.relative_to(ROOT / 'agent')) for file in sorted((ROOT / 'agent/src').glob('*.js'))]:
            zip_entry(archive, 'extension/' + relative, (ROOT / 'agent' / relative).read_bytes())
        zip_entry(archive, 'extension/LICENSE', (ROOT / 'LICENSE').read_bytes())
    vsix = vsix_bytes.getvalue()
    (DIST / 'mobiCode-agent.vsix').write_bytes(vsix)

    files = {'mobiCode-agent.vsix': vsix}
    for file in sorted((ROOT / 'runtime').rglob('*')):
        if file.is_file() and file.name != 'install-template.sh':
            files[str(file.relative_to(ROOT / 'runtime'))] = file.read_bytes()
    tar_bytes = io.BytesIO()
    with tarfile.open(fileobj=tar_bytes, mode='w') as archive:
        for name, content in sorted(files.items()):
            info = tarfile.TarInfo(name)
            info.size, info.mode, info.mtime = len(content), 0o644, 0
            archive.addfile(info, io.BytesIO(content))
    compressed = io.BytesIO()
    with gzip.GzipFile(fileobj=compressed, mode='wb', mtime=0, filename='') as zipped:
        zipped.write(tar_bytes.getvalue())
    installer = (ROOT / 'runtime/install-template.sh').read_text().rstrip() + '\n__MOBICODE_PAYLOAD__\n'
    installer += '\n'.join(textwrap.wrap(base64.b64encode(compressed.getvalue()).decode(), 76)) + '\n'
    encoded = installer.encode()
    if len(base64.b64encode(encoded)) > 120000:
        raise ValueError('Installer exceeds the Android shell argument limit.')
    (DIST / 'mobiCode-setup.sh').write_bytes(encoded)
    (ROOT / 'app/src/main/assets/mobiCode-setup.sh').write_bytes(encoded)
    print(f'Packaged agent ({len(vsix):,} bytes) and installer ({len(encoded):,} bytes).')


if __name__ == '__main__':
    package()
