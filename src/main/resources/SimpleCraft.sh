#!/bin/bash
# ======================================================
# SimpleCraft Game Launcher
# ======================================================
# Description: This script launches the SimpleCraft game,
#              checking for Java prerequisites and running
#              the game JAR with necessary JVM arguments.
# Requirements:
#   - Java 25 or later
#   - SimpleCraft.jar in the same directory as this script
# Usage: ./SimpleCraft.sh [--debug]
#   - Simply run this script to start the game.
#   - Will verify Java installation before launching.
#   - Use --debug to show console output for debugging.
# ======================================================

cd "$(dirname "$0")"

# Check if Java is installed.
if ! command -v java &>/dev/null; then
	echo "Error: Java is not installed or not in PATH."
	echo "Please install Java 25 or later from https://adoptium.net/"
	exit 1
fi

# Check Java version.
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 25 ] 2>/dev/null; then
	JAVA_VERSION_STR=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
	echo "Error: Java 25 or later is required."
	echo "Current version: $JAVA_VERSION_STR"
	exit 1
fi

# Check if JAR file exists.
if [ ! -f "SimpleCraft.jar" ]; then
	echo "Error: SimpleCraft.jar not found in current directory."
	echo "Expected: $(pwd)/SimpleCraft.jar"
	exit 1
fi

# Check if assets folder exists.
if [ ! -d "assets" ]; then
	echo "Warning: assets folder not found. Game may not run correctly."
fi

JVM_ARGS="--add-opens=java.base/java.lang=ALL-UNNAMED \
          --add-opens=java.base/java.nio=ALL-UNNAMED \
          --add-opens=java.base/sun.nio.ch=ALL-UNNAMED \
          --add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
          --enable-native-access=ALL-UNNAMED \
          -XX:+UseZGC"

# Launch game.
if [ "$1" = "--debug" ]; then
	JAVA_VERSION_STR=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
	echo "Found Java version $JAVA_VERSION_STR"
	echo "Starting SimpleCraft..."
	echo
	eval java $JVM_ARGS -jar SimpleCraft.jar
else
	eval nohup java $JVM_ARGS -jar SimpleCraft.jar &>/dev/null &
fi
