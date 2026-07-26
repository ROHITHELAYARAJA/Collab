@echo off
@setlocal

set "JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.18.8-hotspot"
set "MAVEN_PROJECTBASEDIR=%~dp0"

set "BASE_DIR=%MAVEN_PROJECTBASEDIR%"
if "%BASE_DIR:~-1%"=="\" set "BASE_DIR=%BASE_DIR:~0,-1%"

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo Error: JAVA_HOME is not valid: %JAVA_HOME%
    exit /b 1
)

"%JAVA_HOME%\bin\java.exe" -classpath "%BASE_DIR%\.mvn\wrapper\maven-wrapper.jar" "-Dmaven.multiModuleProjectDirectory=%BASE_DIR%" org.apache.maven.wrapper.MavenWrapperMain %*

exit /b %ERRORLEVEL%
