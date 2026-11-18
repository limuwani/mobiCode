#!/usr/bin/env python3
"""Build a reproducible VSIX and first-project archive for Android."""
import gzip
import io
import json
import pathlib
import tarfile
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

    state = '.local/share/mobicode'
    files = {f'{state}/mobiCode-agent.vsix': vsix,
             f'{state}/editor/User/settings.json': (ROOT / 'runtime/settings.json').read_bytes()}
    for file in sorted((ROOT / 'runtime/starter').rglob('*')):
        if file.is_file():
            relative = file.relative_to(ROOT / 'runtime/starter').as_posix()
            files[f'Projects/mobiCode-starter/{relative}'] = file.read_bytes()
    tar_bytes = io.BytesIO()
    with tarfile.open(fileobj=tar_bytes, mode='w') as archive:
        for name, content in sorted(files.items()):
            info = tarfile.TarInfo(name)
            info.size, info.mode, info.mtime = len(content), 0o644, 0
            archive.addfile(info, io.BytesIO(content))
    compressed = io.BytesIO()
    with gzip.GzipFile(fileobj=compressed, mode='wb', mtime=0, filename='') as zipped:
        zipped.write(tar_bytes.getvalue())
    payload = compressed.getvalue()
    (ROOT / 'app/src/main/assets/workspace-starter.tar.gz').write_bytes(payload)
    print(f'Packaged agent ({len(vsix):,} bytes) and starter workspace ({len(payload):,} bytes).')


if __name__ == '__main__':
    package()
