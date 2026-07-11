@echo off
setlocal
set "DIR=%~dp0"
call "%DIR%build-with-jdk.cmd"
if errorlevel 1 exit /b %errorlevel%
set "MVNW=%DIR%mvnw.cmd"
if not exist "%MVNW%" (
  echo Missing %MVNW%
  exit /b 1
)
cd /d "%DIR%"
call "%MVNW%" -q javafx:run
exit /b %errorlevel%
