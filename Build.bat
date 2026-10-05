@echo off
cd /d "%~dp0"
if not exist build mkdir build
for /f "tokens=2,*" %%A in ('java -XshowSettings:properties -version 2^>^&1 ^| findstr /c:"java.home ="') do set "PORTRAIT_JAVA_DIR=%%B"
if not exist "%PORTRAIT_JAVA_DIR%\bin\javac.exe" (
  echo A full JDK 17 or newer is required to rebuild.
  exit /b 1
)
"%PORTRAIT_JAVA_DIR%\bin\javac.exe" --release 17 -d build PortraitRuntime.java PortraitStudio.java AutomaticEngine.java AutomaticStudio.java CourseExporter.java BudgetFitter.java LayerSupport.java LayerPlan.java StudioTheme.java ImageDropHandler.java
if errorlevel 1 exit /b 1
copy /y PortraitRuntime.java build\PortraitRuntime.java >nul
copy /y AutoAnalysis.py build\AutoAnalysis.py >nul
"%PORTRAIT_JAVA_DIR%\bin\jar.exe" --create --file PortraitStudio.jar --main-class AutomaticStudio -C build .
if errorlevel 1 exit /b 1
echo Built PortraitStudio.jar
