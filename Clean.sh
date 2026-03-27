#!/bin/bash
# ======================================================
# SimpleCraft Clean Script
# ======================================================
# Description: This script cleans build artifacts from the
#              SimpleCraft project, removing build outputs
#              and optionally the Gradle cache.
# Usage: ./Clean.sh
#   - Automatically removes the build directory.
#   - Optionally removes the .gradle cache directory.
# ======================================================

echo "================================================="
echo "        SimpleCraft Clean Script"
echo "================================================="
echo

echo "Cleaning build artifacts..."

if [ -f "gradlew" ]; then
	echo "Using Gradle to clean..."
	./gradlew clean
	if [ $? -ne 0 ]; then
		echo
		echo "Warning: Gradle clean failed. Attempting manual cleanup..."
	fi
fi

echo "Removing build directory..."
if [ -d "build" ]; then
	rm -rf "build"
	if [ $? -ne 0 ]; then
		echo "Failed to remove build directory. It may be in use."
	else
		echo "Build directory removed successfully."
	fi
else
	echo "Build directory not found."
fi

echo
read -r -p "Do you want to clean the .gradle cache as well? [y/N] " answer
if [[ "$answer" =~ ^[Yy]$ ]]; then
	echo "Removing .gradle directory..."
	if [ -d ".gradle" ]; then
		rm -rf ".gradle"
		if [ $? -ne 0 ]; then
			echo "Failed to remove .gradle directory. It may be in use."
		else
			echo ".gradle directory removed successfully."
		fi
	else
		echo ".gradle directory not found."
	fi
fi

echo
echo "Cleaning completed."
echo
echo "To rebuild the project, use:"
echo "  * ./Build.sh"
