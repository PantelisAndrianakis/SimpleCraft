#!/bin/bash
# ======================================================
# SimpleCraft Helper Script
# ======================================================
# Description: This script displays help information about
#              SimpleCraft scripts and environment details,
#              including Java and Gradle installation status
#              and build artifacts.
# Usage: ./Help.sh
#   - Displays available scripts and their purposes.
#   - Shows Java and Gradle installation information.
#   - Displays build status of SimpleCraft artifacts.
# ======================================================

echo "================================================="
echo "      SimpleCraft Helper Script"
echo "================================================="
echo
echo "Available scripts:"
echo
echo "  ./SimpleCraft.sh          Run SimpleCraft (requires building first)."
echo "  ./Build.sh                Build SimpleCraft."
echo "  ./Build.sh run            Build and run SimpleCraft."
echo "  ./Build.sh dist           Create distribution package (ZIP)."
echo "  ./Build.sh dist -o        Create package and open folder."
echo "  ./Clean.sh                Clean build artifacts."
echo "  ./Package.sh              Create standalone package (bundled JRE)."
echo "  ./Help.sh                 Display this help information."
echo
echo "================================================="
echo "      Environment Information"
echo "================================================="
echo

echo "Java Information:"
if ! command -v java &>/dev/null; then
	echo "  [ERROR] Java not found in PATH"
else
	java -version 2>&1 | grep version
	echo "  Location: $(command -v java)"
fi
echo

echo "Gradle Information:"
if [ -f "gradlew" ]; then
	echo "  Gradle Wrapper: Present."
else
	echo "  [ERROR] Gradle Wrapper not found."
fi
if [ -f "gradle/wrapper/gradle-wrapper.jar" ]; then
	echo "  Wrapper JAR: Present."
else
	echo "  [ERROR] Wrapper JAR missing."
fi
echo

echo "Build Status:"
if [ -f "build/libs/SimpleCraft.jar" ]; then
	SIZE=$(wc -c < "build/libs/SimpleCraft.jar")
	echo "  SimpleCraft.jar: Present."
	echo "  Size: $SIZE bytes"
else
	echo "  SimpleCraft.jar: Not built yet."
fi

if [ -f "build/distributions/SimpleCraft.zip" ]; then
	SIZE=$(wc -c < "build/distributions/SimpleCraft.zip")
	echo "  Distribution ZIP: Present."
	echo "  Size: $SIZE bytes"
else
	echo "  Distribution ZIP: Not built yet."
fi
echo "================================================="
