# SimpleCraft

A simple voxel game built in Java.

## Requirements

- JDK 25

## Build & Run

### Quick Start
The easiest way to build and run SimpleCraft is to use the provided convenience scripts:

```
# Building
build.bat         # Build only
build.bat run     # Build and run in one step
build.bat dist    # Create distribution package (ZIP)
build.bat dist -o # Create package and open folder

# Running (requires building first)
start.bat         # Runs the game (JAR must exist)

# Clean up build artifacts
clean.bat         # Removes build directories

# Get help and environment info
help.bat          # Displays available scripts and system info
```

### Manual Build
You can also use Gradle commands directly:

```
gradlew.bat build
gradlew.bat run
```

### Distribution Packages
You can create distribution packages using the `build.bat` script with the `dist` parameter:

```
build.bat dist       # Build distribution packages
build.bat dist -o    # Build and open the output folder
```

The distribution package is available in the `build/distributions/` folder as `SimpleCraft.zip`.

This package includes everything needed to run the game. Extract the zip file and use the included `start.bat` to launch the game.

## License

This project is licensed under the terms included in the [LICENSE.txt](LICENSE.txt) file.
