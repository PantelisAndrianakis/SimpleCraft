:: ======================================================
:: SimpleCraft Helper Script
:: ======================================================
:: Description: This script displays help information about
::              SimpleCraft scripts and environment details,
::              including Java and Gradle installation status
::              and build artifacts.
:: Usage: Help.bat
::   - Displays available scripts and their purposes
::   - Shows Java and Gradle installation information
::   - Displays build status of SimpleCraft artifacts
:: ======================================================

@echo off
title SimpleCraft Help
echo =================================================
echo       SimpleCraft Helper Script
echo =================================================
echo.
echo Available scripts:
echo.
echo  Start.bat             Run SimpleCraft (requires building first)
echo  Build.bat             Build SimpleCraft
echo  Build.bat run         Build and run SimpleCraft
echo  Build.bat dist        Create distribution package (ZIP)
echo  Build.bat dist -o     Create package and open folder
echo  Clean.bat             Clean build artifacts
echo  Help.bat              Display this help information
echo.
echo =================================================
echo       Environment Information
echo =================================================
echo.

:: Display Java information.
echo Java Information:
where java >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo  [ERROR] Java not found in PATH
) else (
    java -version 2>&1 | findstr "version"
    echo  Location: 
    where java
)
echo.

:: Check for Gradle.
echo Gradle Information:
if exist "gradlew.bat" (
    echo  Gradle Wrapper: Present
) else (
    echo  [ERROR] Gradle Wrapper not found
)
if exist "gradle\wrapper\gradle-wrapper.jar" (
    echo  Wrapper JAR: Present
) else (
    echo  [ERROR] Wrapper JAR missing
)
echo.

:: Check for build.
echo Build Status:
if exist "build\libs\SimpleCraft.jar" (
    echo  SimpleCraft.jar: Present
    for %%F in ("build\libs\SimpleCraft.jar") do echo  Size: %%~zF bytes
) else (
    echo  SimpleCraft.jar: Not built yet
)

if exist "build\distributions\SimpleCraft.zip" (
    echo  Distribution ZIP: Present
    for %%F in ("build\distributions\SimpleCraft.zip") do echo  Size: %%~zF bytes
) else (
    echo  Distribution ZIP: Not built yet
)
echo.
echo =================================================

pause