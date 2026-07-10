@echo off
setlocal
set "DIR=%~dp0"
if not exist "%DIR%target\classes" mkdir "%DIR%target\classes"
javac -encoding UTF-8 -d "%DIR%target\classes" "%DIR%src\main\java\javax\microedition\lcdui\Font.java" "%DIR%src\main\java\VddohDataEditor.java"
if errorlevel 1 exit /b %errorlevel%
jar --create --file "%DIR%target\vddoh-data-editor-1.0.0.jar" --main-class VddohDataEditor -C "%DIR%target\classes" .
