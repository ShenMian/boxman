# BoxMan

[![CI](https://github.com/ShenMian/boxman/actions/workflows/ci.yml/badge.svg)](https://github.com/ShenMian/boxman/actions/workflows/ci.yml)

This is a port of [BoxMan](https://github.com/yuweng227/BoxMan_And)[^boxman] to the desktop.

[^boxman]: Its original name in Chinese is 推箱快手.

## Requirements

- **JDK 17 or newer.** The bundled Gradle wrapper is 9.7.1, which requires 17+. CI runs on Temurin 17 and 21.
  The compiled bytecode still targets **Java 8** (`sourceCompatibility`/`targetCompatibility = 1.8`), so the produced jar runs on any Java 8+ runtime.
- No Android SDK, no native toolchain, no external solver binary.

## Build and run

```bash
cd desktop

./gradlew run             # compile and launch the game
./gradlew test            # run the JUnit suite
./gradlew fatJar          # build a self-contained jar
java -jar build/libs/BoxManPC-all.jar
```

On Linux CI the tests are run under a virtual display because a handful of them render real Swing windows:

```bash
xvfb-run -a ./gradlew --no-daemon clean test fatJar
```

On Windows use `gradlew.bat`.

## Credits

The original game is the work of [愉翁](https://github.com/yuweng227).
