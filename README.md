<p align="center">
  <img src="assets/images/app_icons/icon_128.png" alt="SimpleCraft Logo" width="128" height="128">
</p>

<h1 align="center">SimpleCraft</h1>

<p align="center">A simple voxel game built in Java.</p>

#

## Requirements

| Component | Requirement |
|-----------|-------------|
| **OS** | Windows 10/11 (64-bit), Linux, or macOS 10.15+ |
| **Processor** | Intel Core i3 or AMD Ryzen 3 (2 cores, 2.5 GHz+) |
| **Memory** | 4 GB RAM |
| **Graphics** | OpenGL 3.2 compatible GPU with 1 GB VRAM |
| **Display** | 1152×864 resolution or higher |
| **Storage** | 500 MB available space |
| **Java** | JDK 25 or compatible runtime |

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

## Acknowledgments

This project was made possible by the following libraries:

- jMonkeyEngine - core game engine and rendering.
- Lemur UI - UI controls and layout.
- OpenSimplex2 noise - included directly as a single Java file (no external dependency).

Thanks to the authors and projects above for making development and distribution of SimpleCraft possible.

## License

This project is licensed under the [MIT License](LICENSE.txt).

