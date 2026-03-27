#!/bin/bash
# ======================================================
# SimpleCraft Build Script
# ======================================================
# Description: This script builds the SimpleCraft project
#              with options for creating distributions
#              and running the game after building.
# Usage: ./Build.sh [options]
#   Options:
#     run, -r, --run     Run the game after building.
#     dist, -d, --dist   Build distribution package.
#     -o, --open         Open folder after building dist.
# ======================================================

echo "================================================="
echo "        SimpleCraft Build Script"
echo "================================================="
echo

RUN_AFTER_BUILD=0
BUILD_DIST=0
OPEN_FOLDER=0

for arg in "$@"; do
	case "$arg" in
		run|-r|--run)   RUN_AFTER_BUILD=1 ;;
		dist|-d|--dist) BUILD_DIST=1 ;;
		-o|--open)      OPEN_FOLDER=1 ;;
	esac
done

echo "Building SimpleCraft..."
echo

if [ $BUILD_DIST -eq 1 ]; then
	./gradlew assembleDist
else
	./gradlew build
fi

if [ $? -ne 0 ]; then
	echo
	echo "Build failed."
	exit 1
fi

echo
echo "Build completed successfully!"

if [ $BUILD_DIST -eq 1 ]; then
	echo
	echo "================================================="
	echo "Distribution package built successfully!"
	echo "================================================="
	echo
	echo "Distribution package is available at:"
	echo " * build/distributions/SimpleCraft.zip"
	echo
	echo "To use a distribution package:"
	echo " 1. Extract the archive."
	echo " 2. Navigate to the extracted SimpleCraft folder."
	echo " 3. Run SimpleCraft.sh to launch the game."
	echo

	if [ $OPEN_FOLDER -eq 1 ]; then
		echo "Opening distributions folder..."
		if [ -d "build/distributions" ]; then
			if command -v xdg-open &>/dev/null; then
				xdg-open "build/distributions"
			elif command -v open &>/dev/null; then
				open "build/distributions"
			fi
		else
			echo "Warning: Could not find distributions folder."
		fi
	fi
fi

if [ $RUN_AFTER_BUILD -eq 1 ]; then
	echo
	echo "================================================="
	echo "        Running SimpleCraft"
	echo "================================================="
	echo
	./gradlew run
else
	echo
	echo "To run the game, use one of:"
	echo "  * ./Build.sh run"
	echo "  * ./Build.sh --run"
	echo "  * ./gradlew run"
fi
