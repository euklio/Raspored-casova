@echo off
setlocal
cd /d "%~dp0"
if not exist out mkdir out
powershell -NoProfile -Command "$files = Get-ChildItem -Recurse -Filter *.java src | ForEach-Object { $_.FullName }; & javac --module-path 'lib' --add-modules javafx.controls,javafx.graphics -encoding UTF-8 -d out @files; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }"
if errorlevel 1 (
  echo Compile failed.
  exit /b 1
)
if not exist out\rasporedcasova\ui mkdir out\rasporedcasova\ui
copy /Y "src\rasporedcasova\ui\app.css" "out\rasporedcasova\ui\app.css" >nul
echo Compile OK.
