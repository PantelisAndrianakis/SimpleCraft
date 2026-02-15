# SimpleCraft

A simple voxel game built in Java.

## Requirements

- JDK 25

## Build & Run

### Quick Start
The easiest way to build and run SimpleCraft is to use the provided convenience scripts:

```
# Building
Build.bat         # Build only.
Build.bat run     # Build and run in one step.
Build.bat dist    # Create distribution package (ZIP).
Build.bat dist -o # Create package and open folder.

# Running (requires building first)
SimpleCraft.bat   # Runs the game (JAR must exist).

# Clean up build artifacts
Clean.bat         # Removes build directories.

# Get help and environment info
Help.bat          # Displays available scripts and system info.
```

### Manual Build
You can also use Gradle commands directly:

```
gradlew.bat build
gradlew.bat run
```

### Distribution Packages
You can create distribution packages using the `Build.bat` script with the `dist` parameter:

```
Build.bat dist       # Build distribution packages.
Build.bat dist -o    # Build and open the output folder.
```

The distribution package is available in the `build/distributions/` folder as `SimpleCraft.zip`.

This package includes everything needed to run the game. Extract the zip file and use the included `SimpleCraft.bat` to launch the game.

## Coding Style

This project follows the [CODING_STYLE.md](CODING_STYLE.md) guidelines:
- Allman brace style (braces on new lines).
- 4-space indentation using tabs.
- 120 character line limit.
- Organized imports with `simplecraft` packages first.

The style is automatically enforced when using VS Code with the provided configuration files in the `.vscode` directory.

## License

This project is licensed under the terms included in the [LICENSE.txt](LICENSE.txt) file.
