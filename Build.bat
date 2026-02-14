:: ======================================================
:: SimpleCraft Build Script
:: ======================================================
:: Description: This script builds the SimpleCraft project
::              with options for creating distributions
::              and running the game after building.
:: Usage: Build.bat [options]
::   Options:
::     run, -r, --run     Run the game after building
::     dist, -d, --dist   Build distribution package
::     -o, --open         Open explorer after building dist
:: ======================================================

@echo off
title SimpleCraft Build
setlocal enabledelayedexpansion

echo =================================================
echo         SimpleCraft Build Script
echo =================================================
echo.

:: Check for arguments.
set RUN_AFTER_BUILD=0
set BUILD_DIST=0
set OPEN_EXPLORER=0

:: Parse command line arguments.
:parse_args
if "%1"=="" goto end_parse
if /i "%1"=="run" set RUN_AFTER_BUILD=1
if /i "%1"=="-r" set RUN_AFTER_BUILD=1
if /i "%1"=="--run" set RUN_AFTER_BUILD=1
if /i "%1"=="dist" set BUILD_DIST=1
if /i "%1"=="-d" set BUILD_DIST=1
if /i "%1"=="--dist" set BUILD_DIST=1
if /i "%1"=="-o" set OPEN_EXPLORER=1
if /i "%1"=="--open" set OPEN_EXPLORER=1
shift
goto parse_args
:end_parse

:: Build the project.
echo Building SimpleCraft...
echo.

if %BUILD_DIST% equ 1 (
    :: Build distributions.
    call gradlew.bat assembleDist
) else (
    :: Normal build.
    call gradlew.bat build
)

:: Check build result.
if %ERRORLEVEL% neq 0 (
    echo.
    echo Build failed with error code %ERRORLEVEL%
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo Build completed successfully!

:: Show distribution information if built.
if %BUILD_DIST% equ 1 (
    echo.
    echo =================================================
    echo Distribution package built successfully!
    echo =================================================
    echo.
    echo Distribution package is available at:
    echo  * build\distributions\SimpleCraft.zip
    echo.
    echo To use a distribution package:
    echo  1. Extract the archive
    echo  2. Navigate to the extracted SimpleCraft folder
    echo  3. Run start.bat to launch the game
    echo.
    
    :: Optionally open the distributions folder.
    if %OPEN_EXPLORER% equ 1 (
        echo Opening distributions folder...
        if exist "build\distributions" (
            start "" "build\distributions"
        ) else (
            echo Warning: Could not find distributions folder.
        )
    )
)

:: Run if requested.
if %RUN_AFTER_BUILD% equ 1 (
    echo.
    echo =================================================
    echo         Running SimpleCraft
    echo =================================================
    echo.
    call gradlew.bat run
) else (
    echo.
    echo To run the game, use one of:
    echo   * build.bat run
    echo   * build.bat --run
    echo   * gradlew.bat run
)

exit /b 0