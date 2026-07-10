@echo off
setlocal
set "DIR=%~dp0"
if not exist "%DIR%target\classes" mkdir "%DIR%target\classes"
if exist "%DIR%target\sources.txt" del "%DIR%target\sources.txt"
for /r "%DIR%src\main\java" %%f in (*.java) do echo %%f>>"%DIR%target\sources.txt"
javac -encoding UTF-8 -d "%DIR%target\classes" @"%DIR%target\sources.txt"
if errorlevel 1 exit /b %errorlevel%
jar --create --file "%DIR%target\vddoh-data-editor-1.0.0.jar" --main-class com.vddoh.editor.VddohDataEditor -C "%DIR%target\classes" .
