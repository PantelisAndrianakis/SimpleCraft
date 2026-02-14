@echo off
setlocal
echo =================================================
echo         SimpleCraft Clean Script
echo =================================================
echo.

echo Cleaning build artifacts...

:: Check if we have Gradle wrapper.
if exist "gradlew.bat" (
    echo Using Gradle to clean...
    call gradlew.bat clean
    
    if %ERRORLEVEL% neq 0 (
        echo.
        echo Warning: Gradle clean failed with error code %ERRORLEVEL%
        echo Attempting manual cleanup...
    )
)

:: Manual cleanup as backup or if Gradle failed.
echo Removing build directory...
if exist "build" (
    rd /s /q "build"
    if %ERRORLEVEL% neq 0 (
        echo Failed to remove build directory. It may be in use.
    ) else (
        echo Build directory removed successfully.
    )
) else (
    echo Build directory not found.
)

:: Clean .gradle cache (optional).
echo.
choice /C YN /M "Do you want to clean the .gradle cache as well?"
if %ERRORLEVEL% EQU 1 (
    echo Removing .gradle directory...
    if exist ".gradle" (
        rd /s /q ".gradle"
        if %ERRORLEVEL% neq 0 (
            echo Failed to remove .gradle directory. It may be in use.
        ) else (
            echo .gradle directory removed successfully.
        )
    ) else (
        echo .gradle directory not found.
    )
)

echo.
echo Cleaning completed.
echo.
echo To rebuild the project, use:
echo   * Build.bat
echo   * Start.bat

exit /b 0