@echo off
rem Builds butler-turnstile.exe with MSVC. Called by desktop/build.gradle.kts:
rem   build.cmd <WebView2 SDK folder> <output folder>
setlocal
set "SDK=%~1"
set "OUT=%~2"
set "HERE=%~dp0"
set "VSWHERE=%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe"
set "VSPATH="
for /f "usebackq delims=" %%i in (`call "%VSWHERE%" -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath`) do set "VSPATH=%%i"
if not defined VSPATH (
  echo MSVC isn't installed: Visual Studio Build Tools with the C++ workload is needed.
  exit /b 1
)
call "%VSPATH%\VC\Auxiliary\Build\vcvars64.bat" >nul || exit /b 1
if not exist "%OUT%" mkdir "%OUT%"
cl /nologo /EHsc /O2 /std:c++17 /DUNICODE /D_UNICODE /I "%SDK%\build\native\include" ^
  "%HERE%turnstile.cpp" /Fo"%OUT%\\" /Fe"%OUT%\butler-turnstile.exe" ^
  /link /SUBSYSTEM:WINDOWS "%SDK%\build\native\x64\WebView2LoaderStatic.lib" ^
  user32.lib ole32.lib shlwapi.lib advapi32.lib gdi32.lib shell32.lib || exit /b 1
del "%OUT%\*.obj" >nul 2>&1
