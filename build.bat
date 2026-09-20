@echo off
rem ============================================================
rem  CoreGeek (Future War) build script for Windows
rem  Usage: build.bat
rem  Output: .class files under bin\
rem ============================================================
setlocal
cd /d "%~dp0"

rem ---- locate javac: JAVA_HOME > PATH > common fallback ----
set "JAVAC=javac"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" set "JAVAC=%JAVA_HOME%\bin\javac.exe"
if not exist "%~dp0bin" mkdir bin
if "%JAVAC%"=="javac" if exist "D:\Java\jdk1.8.0_311\bin\javac.exe" set "JAVAC=D:\Java\jdk1.8.0_311\bin\javac.exe"

cd src
"%JAVAC%" -Xlint:unchecked -Xlint:-options -source 1.8 -target 1.8 ^
  -encoding UTF-8 -cp "..\lib\*" -d ..\bin @..\makelist.txt

if errorlevel 1 (
  echo [BUILD FAILED]
  exit /b 1
) else (
  echo [BUILD OK] classes output to bin\
  exit /b 0
)
