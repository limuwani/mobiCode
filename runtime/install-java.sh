#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
pkg install -y openjdk-21
java -version
javac -version
echo 'Java tools installed. Open Hello.java in the starter project.'
