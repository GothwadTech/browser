@echo off
setlocal enabledelayedexpansion
title Uninstall Gothwad TV Browser

echo =========================================================
echo       Uninstalling Gothwad TV Browser...
echo =========================================================
echo.

:: Remove shortcuts
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$desktop = [Environment]::GetFolderPath('Desktop') + '\Gothwad TV Browser.lnk';" ^
    "$startMenu = [Environment]::GetFolderPath('Programs') + '\Gothwad TV Browser.lnk';" ^
    "if (Test-Path $desktop) { Remove-Item -Force $desktop };" ^
    "if (Test-Path $startMenu) { Remove-Item -Force $startMenu };"

:: Uninstall APK if adb is connected
where adb >nul 2>&1
if %errorlevel% equ 0 (
    adb connect 127.0.0.1:58526 >nul 2>&1
    adb uninstall com.bansify.internet >nul 2>&1
)

:: Remove installed directory
set "INSTALL_DIR=%LOCALAPPDATA%\GothwadBrowser"
if exist "%INSTALL_DIR%" (
    rmdir /s /q "%INSTALL_DIR%"
)

echo.
echo Gothwad TV Browser has been successfully uninstalled from your PC.
echo.
pause
