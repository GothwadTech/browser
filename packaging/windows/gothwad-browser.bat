@echo off
setlocal enabledelayedexpansion
title Gothwad TV Browser

set "PACKAGE=com.bansify.internet"
set "ACTIVITY=com.bansify.internet/.activity.main.MainActivity"
set "SCRIPT_DIR=%~dp0"
set "APK_FILE=%SCRIPT_DIR%app.apk"

:: Check for WSA (Windows Subsystem for Android) client
set "WSA_CLIENT=%LOCALAPPDATA%\Microsoft\WindowsApps\MicrosoftCorporationII.WindowsSubsystemForAndroid_8wekyb3d8bbwe\WsaClient.exe"

if exist "%WSA_CLIENT%" (
    start "" "%WSA_CLIENT%" /launch wsa://%PACKAGE%
    exit /b 0
)

:: Try ADB if installed or available
where adb >nul 2>&1
if %errorlevel% equ 0 (
    :: Try connecting to WSA default port
    adb connect 127.0.0.1:58526 >nul 2>&1

    :: Check if package is installed
    adb shell pm list packages %PACKAGE% 2>nul | findstr /i "%PACKAGE%" >nul
    if %errorlevel% neq 0 (
        if exist "%APK_FILE%" (
            echo Installing Gothwad TV Browser on Android subsystem...
            adb install -r "%APK_FILE%"
        )
    )

    :: Launch application
    adb shell am start -n %ACTIVITY% >nul 2>&1
    if %errorlevel% equ 0 (
        exit /b 0
    )
)

:: If neither WSA nor ADB worked, open user guide
echo =======================================================
echo          Gothwad TV Browser for Windows
echo =======================================================
echo.
echo To run Gothwad TV Browser on Windows:
echo 1. If using Windows 11 with WSA:
echo    Double click 'Install.bat' or install 'app.apk' directly.
echo 2. Or install WSA-PacMan (easiest one-click installer for Windows 11):
echo    https://github.com/LSPosed/WSA-PacMan/releases
echo 3. Or use any emulator like BlueStacks, Nox, or LDPlayer.
echo.
echo Included APK location: %APK_FILE%
echo =======================================================
echo.
pause
