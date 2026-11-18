# Welcome to mobiCode

This project lives on your tablet in your mobiCode workspace.

Open **Terminal → New Terminal** or tap **Terminal** in mobiCode's bottom bar.

## C++

```sh
g++ -std=c++17 main.cpp -o main
./main
```

## Python

```sh
python hello.py
```

## Java

Java is ready after the initial setup:

```sh
javac Hello.java
java Hello
```

## Web preview

```sh
python -m http.server 3000 --bind 127.0.0.1
```

Open http://127.0.0.1:3000/index.html in your tablet browser. Stop the preview with Ctrl+C.

## Projects and extensions

Clone repositories into `~/Projects`, then use **File → Open Folder**. Use the
extension panel for compatible Linux extensions from Open VSX.

## AI

Tap **Agent**, then **Provider**. Enter your provider's chat-completions base
URL, exact model ID and optional API key. Chat works without tools. Agent mode
needs a model supporting function/tool calls. Code and outputs you include are
sent to your provider. Keys are stored using the editor's secret storage.

Each agent file change opens a diff. Apply the edit, then save it. Shell commands
require a separate review and run with access to the mobiCode workspace. Your
provider may charge for its service.
