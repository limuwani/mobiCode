# Welcome to mobiCode

This project lives on your tablet, inside Termux's home directory.

Open **Terminal → New Terminal** or tap **Terminal** in mobiCode's bottom bar.

## C++

```sh
clang++ -std=c++17 main.cpp -o main
./main
```

## Python

```sh
python hello.py
```

## Java

Install the Java tools once, then compile and run:

```sh
bash ~/.local/share/mobicode/install-java.sh
javac Hello.java
java Hello
```

## Web preview

```sh
python -m http.server 3000 --bind 127.0.0.1
```

Open http://127.0.0.1:3000/index.html in your tablet browser. Stop the preview with Ctrl+C.

## Projects and extensions

Clone repositories into `~/Projects`, then use **File → Open Folder**. Keep Git
projects out of `/sdcard`; Android shared storage lacks the usual Linux file
permissions and symbolic links. Use the extension panel for compatible Open VSX
extensions. Android-native dependencies need individual compatibility checks.

## AI

Tap **Agent**, then **Provider**. Enter your provider's chat-completions base
URL, exact model ID and optional API key. Chat works without tools. Agent mode
needs a model supporting function/tool calls. Code and outputs you include are
sent to your provider. Keys are stored using the editor's secret storage.

Each agent file change opens a diff. Apply the edit, then save it. Shell commands
require a separate review and run with Termux's permissions; they are not an OS
sandbox. Your provider may charge for its service.
