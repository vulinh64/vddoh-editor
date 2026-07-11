@echo off
setlocal
set "DIR=%~dp0"
call "%DIR%build-with-jdk.cmd"
if errorlevel 1 exit /b %errorlevel%
set "JAR=%DIR%target\vddoh-data-editor-1.0.0.jar"
if not exist "%JAR%" (
  echo Missing %JAR%
  exit /b 1
)
cd /d "%DIR%"
java -jar "%JAR%" %*
exit /b %errorlevel%
