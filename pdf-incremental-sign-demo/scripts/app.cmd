@echo off

setlocal

set SCRIPT_DIR=%~dp0

if "%SCRIPT_DIR:~-1%"=="\" set SCRIPT_DIR=%SCRIPT_DIR:~0,-1%

set PROJECT_ROOT=%SCRIPT_DIR%\..

pushd "%PROJECT_ROOT%" >nul

set PROJECT_ROOT=%CD%

popd >nul

set JAR=%PROJECT_ROOT%\target\pdf-incremental-sign-demo-1.0-SNAPSHOT-jar-with-dependencies.jar

if not exist "%JAR%" (

  echo [app] Missing CLI jar at %JAR%. Build with "mvn -q -DskipTests package".

  exit /b 1

)

java -jar "%JAR%" %*

