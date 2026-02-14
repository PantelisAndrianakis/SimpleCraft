@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m" "--enable-native-access=ALL-UNNAMED" "--add-opens=java.base/java.lang=ALL-UNNAMED" "--add-opens=java.base/java.nio=ALL-UNNAMED" "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED" "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED"

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH. 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME% 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:execute
@rem Setup the command line

@rem Check for wrapper JAR and download if missing
if not exist "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" (
    echo Gradle wrapper JAR not found. Attempting to download...
    
    @rem Make sure the directory exists
    if not exist "%APP_HOME%\gradle\wrapper" mkdir "%APP_HOME%\gradle\wrapper"
    
    @rem Use PowerShell to download the file
    powershell -Command "& {Invoke-WebRequest -Uri 'https://github.com/gradle/gradle/raw/v9.3.1/gradle/wrapper/gradle-wrapper.jar' -OutFile '%APP_HOME%\gradle\wrapper\gradle-wrapper.jar'}"
    
    if exist "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" (
        echo Gradle wrapper JAR downloaded successfully!
    ) else (
        echo Failed to download Gradle wrapper JAR.
        goto fail
    )
)

@rem Check for wrapper properties file and create if missing
if not exist "%APP_HOME%\gradle\wrapper\gradle-wrapper.properties" (
    echo Gradle wrapper properties file not found. Creating default...
    
    @rem Make sure the directory exists
    if not exist "%APP_HOME%\gradle\wrapper" mkdir "%APP_HOME%\gradle\wrapper"
    
    @rem Create default properties file
    (
        echo distributionBase=GRADLE_USER_HOME
        echo distributionPath=wrapper/dists
        echo distributionUrl=https://services.gradle.org/distributions/gradle-9.3.1-bin.zip
        echo networkTimeout=10000
        echo validateDistributionUrl=true
        echo zipStoreBase=GRADLE_USER_HOME
        echo zipStorePath=wrapper/dists
    ) > "%APP_HOME%\gradle\wrapper\gradle-wrapper.properties"
    
    if exist "%APP_HOME%\gradle\wrapper\gradle-wrapper.properties" (
        echo Gradle wrapper properties file created successfully!
    ) else (
        echo Failed to create Gradle wrapper properties file.
        goto fail
    )
)

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:end
@rem End local scope for the variables with windows NT shell
if %OS%==Windows_NT endlocal

:omega
exit /b %ERRORLEVEL%

:fail
@rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of
@rem the _cmd.exe /c_ return code!
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%GRADLE_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%
