#!/usr/bin/env python3
"""Stage the official Android PRoot binaries and the sources for their release."""
import hashlib
import pathlib
import shutil
import subprocess
import tempfile
import urllib.request
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
TERMUX = 'https://packages.termux.dev/apt/termux-main/pool/main/'
PACKAGES = {
    'proot': ('p', '5.1.107.102',
              '10a394b585c2fc2b2c8502544f4e5f1be581dc8bfff79e8ba62f16207423e030',
              '2497064226c15971be8131c7facead94a06c225347796326a383c832dca57758'),
    'libtalloc': ('libt', '2.5.0',
                  '556591f43bb773ad8777e1a29522640866a55f95dab71914418b94a8c58ad5a7',
                  'b8c6d95f20075dc1f9ec6573575b2444e8d526e48e0d8d6d5cf4e071e6e06530'),
    'libandroid-shmem': ('liba', '0.7',
                        '0da3a24d558b93c92bcf8d611e0826a99ff96e396b148e6cdf33b47c47c57ff6',
                        'ffa9e4c87467b158b148d0ff92dda796aa038276c2075af3269cdcdb06f25797')
}
SOURCES = {
    'proot-5.1.107.102.zip': (
        'https://github.com/termux/proot/archive/v5.1.107.102.zip',
        '30d12a6e2cc4147123756ff58edea262d3083bbc238c12d0ab9b39e3d1c40852'),
    'talloc-2.5.0.tar.gz': (
        'https://www.samba.org/ftp/talloc/talloc-2.5.0.tar.gz',
        '912afa237510ae542a7733998eb18a12bcda35ab6729c8e2ddb43e8d0ebab007'),
    'libandroid-shmem-0.7.tar.gz': (
        'https://github.com/termux/libandroid-shmem/archive/refs/tags/v0.7.tar.gz',
        '1e5ff8459bc0a8c229dd8a94b27d119987e09ef3414331c2b5ebfff20b98e867')
}


def download(url, digest, destination):
    with urllib.request.urlopen(url, timeout=60) as response, destination.open('wb') as target:
        shutil.copyfileobj(response, target)
    if hashlib.sha256(destination.read_bytes()).hexdigest() != digest:
        raise ValueError(f'Checksum mismatch for {destination.name}')


def main():
    with tempfile.TemporaryDirectory(prefix='mobicode-native-') as folder:
        temporary = pathlib.Path(folder)
        for index, (arch, abi) in enumerate([('aarch64', 'arm64-v8a'), ('x86_64', 'x86_64')]):
            destination = ROOT / 'app/src/main/jniLibs' / abi
            destination.mkdir(parents=True, exist_ok=True)
            for name, (group, version, *digests) in PACKAGES.items():
                filename = f'{name}_{version}_{arch}.deb'
                package = temporary / filename
                download(f'{TERMUX}{group}/{name}/{filename}', digests[index], package)
                subprocess.run(['dpkg-deb', '-x', package, temporary / arch], check=True)
            prefix = temporary / arch / 'data/data/com.termux/files/usr'
            launcher = (prefix / 'bin/proot').read_bytes()
            # Android extracts only lib*.so filenames. Keep the ELF string table size fixed.
            soname = b'libtalloc.so.2\0'
            if launcher.count(soname) != 1:
                raise ValueError(f'Unexpected PRoot dynamic library reference on {abi}')
            (destination / 'libproot.so').write_bytes(launcher.replace(soname, b'libtalloc.so\0\0\0'))
            shutil.copyfile(prefix / 'lib/libtalloc.so.2', destination / 'libtalloc.so')
            shutil.copyfile(prefix / 'lib/libandroid-shmem.so', destination / 'libandroid-shmem.so')
            for loader in ('loader', 'loader32'):
                source = prefix / 'libexec/proot' / loader
                if source.is_file():
                    shutil.copyfile(source, destination / f'libproot_{loader}.so')
            subprocess.run(['readelf', '-d', destination / 'libproot.so'], check=True, stdout=subprocess.DEVNULL)
            print(f'Prepared PRoot native binaries for {abi}.')

        dist = ROOT / 'dist'
        dist.mkdir(exist_ok=True)
        with zipfile.ZipFile(dist / 'mobiCode-native-sources.zip', 'w') as archive:
            for filename, (url, digest) in SOURCES.items():
                target = temporary / filename
                download(url, digest, target)
                archive.write(target, filename)
            archive.write(__file__, 'native_runtime.py')
            archive.write(ROOT / 'THIRD_PARTY_NOTICES.md', 'THIRD_PARTY_NOTICES.md')


if __name__ == '__main__':
    main()
