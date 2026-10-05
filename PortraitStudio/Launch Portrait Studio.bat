@echo off
cd /d "%~dp0"
java -jar PortraitStudio.jar
if errorlevel 1 (
  echo.
  echo Portrait Studio requires a full Java JDK 17 or newer.
  pause
)
