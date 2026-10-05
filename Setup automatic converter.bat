@echo off
cd /d "%~dp0"
python -m venv .venv
if errorlevel 1 goto failed
".venv\Scripts\python.exe" -m pip install -r requirements.txt
if errorlevel 1 goto failed
echo Automatic converter is ready. Open Launch Portrait Studio.bat.
pause
exit /b 0
:failed
echo Setup failed. Install Python 3.10 or newer with pip and retry.
pause
exit /b 1
