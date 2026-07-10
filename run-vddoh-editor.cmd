@echo off
setlocal
set "DIR=%~dp0"
set "JAR=%DIR%target\vddoh-data-editor-1.0.0.jar"
if not exist "%JAR%" (
  echo Missing %JAR%
  echo Build it with: mvn package
  echo Or run build-with-jdk.cmd when Maven is unavailable.
  exit /b 1
)
java -jar "%JAR%" %*
