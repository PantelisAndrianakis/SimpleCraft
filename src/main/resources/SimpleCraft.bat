@echo off
setlocal enabledelayedexpansion
echo =================================================
echo       Starting SimpleCraft
echo =================================================
echo.

:: Check if Java is installed and has correct version.
echo Checking Java installation...
where java >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo Error: Java not found in PATH!
    echo Please install Java 25 or later and ensure it's in your PATH.
    echo Alternatively, set the JAVA_HOME environment variable.
    pause
    exit /b 1
)

:: Verify Java version.
for /f "tokens=3" %%g in ('java -version 2^>^&1 ^| find "version"') do (
    set JAVA_VERSION=%%g
)

set JAVA_VERSION=!JAVA_VERSION:"=!
set JAVA_VERSION=!JAVA_VERSION:~0,2!

if !JAVA_VERSION! LSS 25 (
    echo Warning: SimpleCraft requires Java 25 or later.
    echo Your Java version appears to be !JAVA_VERSION!
    echo The application may not run correctly.
    echo.
    
    choice /C YN /M "Do you want to continue anyway?"
    if !ERRORLEVEL! EQU 2 exit /b 0
    echo.
)

:: Check if JAR file exists.
set JAR_PATH=SimpleCraft\libs\SimpleCraft.jar
if not exist "%JAR_PATH%" (
    echo Error: SimpleCraft JAR not found at %JAR_PATH%
    echo.
    echo Please build the project first by running:
    echo   Build.bat
    echo.
    pause
    exit /b 1
)

:: Add JVM args needed for jMonkeyEngine.
set JVM_ARGS=--add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED

:: Run the JAR directly.
echo Running SimpleCraft...
java %JVM_ARGS% -jar %JAR_PATH%

if %ERRORLEVEL% neq 0 (
    echo.
    echo SimpleCraft failed to run with error code %ERRORLEVEL%
    pause
    exit /b %ERRORLEVEL%
)

exit /b 0