# Third-party notices

The mobiCode code and supplied logo use the MIT licence. Copyright 2026
Limuwani Nethananni.

The Gradle wrapper is from [Gradle v8.11.1](https://github.com/gradle/gradle/tree/v8.11.1/gradle/wrapper)
under Apache-2.0. See `gradle/LICENSE-APACHE-2.0.txt`.

The APK aggregates the Termux builds of PRoot 5.1.107.102, talloc 2.5.0 and
libandroid-shmem 0.7. The authors' licence texts and sources are included in
`mobiCode-native-sources.zip` alongside the APK. PRoot's executable references
a shortened talloc library name to match Android APK packaging. See
`tools/native_runtime.py` for the exact packages and the packaging transformation.
Upstream build recipes come from the pinned
[Termux package source](https://github.com/termux/termux-packages/tree/84e2a370eb92898242d4ef956f22931280131460).

Archive extraction uses [Apache Commons Compress 1.28.0](https://commons.apache.org/proper/commons-compress/)
under Apache-2.0. Ubuntu, code-server, and language packages are downloaded from
their upstream distributors during setup and keep their own licences.
