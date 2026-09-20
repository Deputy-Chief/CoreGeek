@echo off
rem ============================================================
rem  CoreGeek (Future War) run script for Windows
rem  Usage: run.bat [port]      default port 8080
rem ============================================================
setlocal
cd /d "%~dp0"

if not exist bin (
  echo [ERROR] bin\ not found. Run build.bat first.
  exit /b 1
)

rem ---- locate java: JAVA_HOME > PATH > common fallback ----
set "JAVA=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA=%JAVA_HOME%\bin\java.exe"
if "%JAVA%"=="java" if exist "D:\Java\jdk1.8.0_311\bin\java.exe" set "JAVA=D:\Java\jdk1.8.0_311\bin\java.exe"

set PORT=8080
if not "%1"=="" set PORT=%1

"%JAVA%" -cp "bin;lib\*" com.huawei.Main %PORT%
