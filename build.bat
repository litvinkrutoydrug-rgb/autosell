@echo off
chcp 65001 >nul
cd /d "%~dp0"
title Сборка мода AutoSell

echo ============================================
echo   Сборка мода AutoSell (Fabric 1.21.4)
echo ============================================
echo.

where java >nul 2>nul
if errorlevel 1 (
    echo [ОШИБКА] Java не найдена.
    echo Установите JDK 21: https://adoptium.net/ и запустите этот файл снова.
    echo.
    pause
    exit /b 1
)

echo Идёт сборка. Первый раз это занимает несколько минут, нужен интернет...
echo.
call gradlew.bat build
if errorlevel 1 (
    echo.
    echo [ОШИБКА] Сборка не удалась. Сделайте скриншот или скопируйте текст выше.
    echo.
    pause
    exit /b 1
)

echo.
echo ============================================
echo   ГОТОВО!
echo   Файл мода: build\libs\autosell-1.0.0.jar
echo   Скопируйте ЕГО (не sources) в папку mods.
echo ============================================
start "" explorer "%~dp0build\libs"
pause
