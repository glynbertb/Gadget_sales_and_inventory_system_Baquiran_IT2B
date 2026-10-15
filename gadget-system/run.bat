@echo off
REM Bertzkie Gadget Sales and Inventory System - Windows launcher
REM Uses --enable-native-access to suppress JDK 24+ warning about SQLite native library
REM Run from project root or gadget-system folder; adjusts Gadget.db path automatically

setlocal
set SCRIPT_DIR=%~dp0
REM Prefer JDK 17 if available to avoid native-access warning, otherwise use default java with flag
where java >nul 2>&1
if errorlevel 1 (
  echo [!] java not found in PATH. Please install JDK 17+ and add it to PATH.
  pause
  exit /b 1
)

REM Check if Gadget.db is in parent folder (when running from gadget-system\target)
if exist "%SCRIPT_DIR%..\..\Gadget.db" (
  set DB_PATH=%SCRIPT_DIR%..\..\Gadget.db
) else if exist "%SCRIPT_DIR%Gadget.db" (
  set DB_PATH=%SCRIPT_DIR%Gadget.db
) else if exist "Gadget.db" (
  set DB_PATH=Gadget.db
) else (
  set DB_PATH=Gadget.db
)

REM Detect java version - if 24+ we need --enable-native-access
for /f "tokens=3" %%g in ('java -version 2^>^&1 ^| findstr /i "version"') do set JAVA_VER=%%g
REM Fallback: just always pass the flag (harmless on Java 17, required on 25/26)
java --enable-native-access=ALL-UNNAMED -jar "%SCRIPT_DIR%target\gadget-sales-inventory-1.0.0.jar" %*
if errorlevel 1 (
  echo.
  echo [!] Launch failed. Trying without --enable-native-access flag...
  java -jar "%SCRIPT_DIR%target\gadget-sales-inventory-1.0.0.jar" %*
)
endlocal
