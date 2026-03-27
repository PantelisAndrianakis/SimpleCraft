:: ======================================================
:: SimpleCraft Package Script
:: ======================================================
:: Description: This script builds the SimpleCraft project
::              with bundled JRE.
::              No Java install required on target machine.
:: Usage: Package.bat 
:: ======================================================

@echo off
echo Building SimpleCraft standalone package (embedded JRE)...
echo.
call gradlew.bat standalonePackage
if %ERRORLEVEL% neq 0 (
    echo.
    echo Build failed. See output above for details.
    exit /b 1
)
