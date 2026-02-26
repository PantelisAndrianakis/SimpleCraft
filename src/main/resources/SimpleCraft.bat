:: ======================================================
:: SimpleCraft Game Launcher
:: ======================================================
:: Description: This script launches the SimpleCraft game,
::              checking for Java prerequisites and running
::              the game JAR with necessary JVM arguments.
:: Requirements:
::   - Java 25 or later
::   - SimpleCraft.jar built using Build.bat
:: Usage: SimpleCraft.bat [/debug]
::   - Simply run this script to start the game.
::   - Will verify Java installation before launching.
::   - Use /debug to show console window for debugging.
:: ======================================================

@echo off
title SimpleCraft
setlocal enabledelayedexpansion

:: Check if Java is installed.
java -version >nul 2>&1
if %ERRORLEVEL% neq 0 (
	echo Error: Java is not installed or not in PATH.
	echo Please install Java 25 or later from https://adoptium.net/
	pause
	exit /b 1
)

:: Check Java version.
for /f "tokens=3" %%g in ('java -version 2^>^&1 ^| findstr /i "version"') do (
	set JAVA_VERSION_STR=%%g
	set JAVA_VERSION_STR=!JAVA_VERSION_STR:"=!
	for /f "delims=.-_ tokens=1" %%v in ("!JAVA_VERSION_STR!") do set JAVA_VERSION=%%v
)

if !JAVA_VERSION! LSS 25 (
	echo Error: Java 25 or later is required.
	echo Current version: !JAVA_VERSION_STR!
	pause
	exit /b 1
)

:: Check if JAR file exists.
set JAR_PATH=SimpleCraft.jar
if not exist "%JAR_PATH%" (
	echo Error: SimpleCraft.jar not found in current directory.
	echo Expected: %CD%\%JAR_PATH%
	pause
	exit /b 1
)

:: Check if assets folder exists.
if not exist "assets" (
	echo Warning: assets folder not found. Game may not run correctly.
)

:: Launch game.
if "%1"=="/debug" (
	echo Found Java version !JAVA_VERSION_STR!
	echo Starting SimpleCraft...
	echo.
	java --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED -XX:+UseZGC -jar SimpleCraft.jar
	
	:: Pause if there was an error.
	if %ERRORLEVEL% neq 0 (
		echo.
		echo SimpleCraft failed to run with error code %ERRORLEVEL%
		pause
	)
) else (
	:: Normal mode - no console.
	start /min javaw --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED -XX:+UseZGC -jar SimpleCraft.jar
)
