# BoxMan

[![CI](https://github.com/ShenMian/boxman/actions/workflows/ci.yml/badge.svg)](https://github.com/ShenMian/boxman/actions/workflows/ci.yml)

This is a port of [BoxMan](https://github.com/yuweng227/BoxMan_And)[^boxman] to the desktop.

[^boxman]: Its original name in Chinese is 推箱快手.

## Requirements

1. **Build requirement**: JDK 17 or newer. The bundled Gradle wrapper requires 17+.
2. **Runtime requirement**: Java 8 or newer. The compiled bytecode targets Java 8, so the produced jar runs on any Java 8+ runtime.

## Build and run

```bash
cd desktop

./gradlew run             # compile and launch the game
./gradlew test            # run the JUnit suite
./gradlew fatJar          # build a self-contained jar
java -jar build/libs/BoxManPC-all.jar
```

On Windows use `gradlew.bat`.

## Credits

The original game is the work of [愉翁](https://github.com/yuweng227).
