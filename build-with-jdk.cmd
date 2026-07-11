@echo off
setlocal
set "DIR=%~dp0"

call "%DIR%mvnw.cmd" -q -DskipTests package
exit /b %errorlevel%
