@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"
set "LOG=%~dp0build.log"
echo === Claude Launcher build ===> "%LOG%"
echo %DATE% %TIME%>> "%LOG%"

rem ---------- locate Android SDK ----------
set "SDK=%LOCALAPPDATA%\Android\Sdk"
if not exist "%SDK%\platforms" if defined ANDROID_HOME set "SDK=%ANDROID_HOME%"
if not exist "%SDK%\platforms" if defined ANDROID_SDK_ROOT set "SDK=%ANDROID_SDK_ROOT%"
if not exist "%SDK%\platforms" (
  echo ERROR: Android SDK not found>> "%LOG%"
  goto fail
)
echo SDK=%SDK%>> "%LOG%"

rem ---------- pick newest build-tools ----------
set "BT="
for /f "delims=" %%d in ('dir /b /ad /o-n "%SDK%\build-tools" 2^>nul') do (
  if not defined BT set "BT=%SDK%\build-tools\%%d"
)
if not defined BT (
  echo ERROR: build-tools not found>> "%LOG%"
  goto fail
)
echo BUILD_TOOLS=%BT%>> "%LOG%"

rem ---------- pick platform android.jar ----------
set "PLAT="
for /f "delims=" %%d in ('dir /b /ad /o-n "%SDK%\platforms" 2^>nul') do (
  if not defined PLAT if exist "%SDK%\platforms\%%d\android.jar" set "PLAT=%SDK%\platforms\%%d\android.jar"
)
if not defined PLAT (
  echo ERROR: android.jar not found>> "%LOG%"
  goto fail
)
echo PLATFORM=%PLAT%>> "%LOG%"

rem ---------- locate a JDK ----------
set "JH="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" set "JH=%JAVA_HOME%"
if not defined JH if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\javac.exe" set "JH=%ProgramFiles%\Android\Android Studio\jbr"
if not defined JH if exist "%LOCALAPPDATA%\Programs\Android Studio\jbr\bin\javac.exe" set "JH=%LOCALAPPDATA%\Programs\Android Studio\jbr"
if not defined JH if exist "%ProgramFiles%\Android\Android Studio1\jbr\bin\javac.exe" set "JH=%ProgramFiles%\Android\Android Studio1\jbr"
if not defined JH (
  for /f "delims=" %%j in ('where javac 2^>nul') do (
    if not defined JH set "JH=%%~dpj.."
  )
)
if not defined JH (
  echo ERROR: JDK not found. Install Android Studio or set JAVA_HOME.>> "%LOG%"
  goto fail
)
set "JAVA_HOME=%JH%"
set "PATH=%JH%\bin;%PATH%"
echo JAVA_HOME=%JH%>> "%LOG%"
"%JH%\bin\java.exe" -version >> "%LOG%" 2>&1

rem ---------- clean ----------
if exist build rd /s /q build
mkdir build
mkdir build\gen
mkdir build\classes
mkdir build\dex

rem ---------- 1. compile resources ----------
echo.>> "%LOG%"
echo [1/7] aapt2 compile>> "%LOG%"
"%BT%\aapt2.exe" compile --dir res -o build\res.zip >> "%LOG%" 2>&1
if errorlevel 1 goto fail

rem ---------- 2. link resources ----------
echo [2/7] aapt2 link>> "%LOG%"
"%BT%\aapt2.exe" link -o build\base.apk -I "%PLAT%" --manifest AndroidManifest.xml --java build\gen --min-sdk-version 29 --target-sdk-version 35 build\res.zip >> "%LOG%" 2>&1
if errorlevel 1 goto fail

rem ---------- 3. compile java ----------
rem NOTE: relative paths only. An absolute path would contain the Japanese
rem user name, and javac reads @argfiles as UTF-8 while cmd writes cp932.
rem If you add a new .java file, add it to SRC below.
echo [3/7] javac>> "%LOG%"
set "SRC=src\com\rerise\claudelauncher\Entry.java src\com\rerise\claudelauncher\Store.java src\com\rerise\claudelauncher\IconGen.java src\com\rerise\claudelauncher\Updater.java src\com\rerise\claudelauncher\OpenActivity.java src\com\rerise\claudelauncher\MainActivity.java"
if exist build\gen\com\rerise\claudelauncher\R.java set "SRC=!SRC! build\gen\com\rerise\claudelauncher\R.java"
"%JH%\bin\javac.exe" -encoding UTF-8 -source 17 -target 17 -nowarn -Xlint:-options -classpath "%PLAT%" -d build\classes !SRC! >> "%LOG%" 2>&1
if errorlevel 1 goto fail

rem ---------- 4. jar + dex ----------
echo [4/7] d8>> "%LOG%"
"%JH%\bin\jar.exe" cf build\classes.jar -C build\classes . >> "%LOG%" 2>&1
if errorlevel 1 goto fail
call "%BT%\d8.bat" --lib "%PLAT%" --min-api 29 --output build\dex build\classes.jar >> "%LOG%" 2>&1
if errorlevel 1 goto fail

rem ---------- 5. put classes.dex into the apk ----------
echo [5/7] package>> "%LOG%"
copy /y build\base.apk build\unsigned.apk >> "%LOG%" 2>&1
pushd build\dex
"%JH%\bin\jar.exe" ufM ..\unsigned.apk classes.dex >> "%LOG%" 2>&1
popd
if not exist build\unsigned.apk goto fail

rem ---------- 6. align ----------
echo [6/7] zipalign>> "%LOG%"
"%BT%\zipalign.exe" -f -p 4 build\unsigned.apk build\aligned.apk >> "%LOG%" 2>&1
if errorlevel 1 goto fail

rem ---------- 7. sign ----------
echo [7/7] apksigner>> "%LOG%"
if not exist debug.keystore (
  "%JH%\bin\keytool.exe" -genkeypair -v -keystore debug.keystore -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Claude Launcher, O=ReRISE, C=JP" >> "%LOG%" 2>&1
)
if exist ClaudeLauncher.apk del ClaudeLauncher.apk
call "%BT%\apksigner.bat" sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey --out ClaudeLauncher.apk build\aligned.apk >> "%LOG%" 2>&1
if errorlevel 1 goto fail
if not exist ClaudeLauncher.apk goto fail

call "%BT%\apksigner.bat" verify --print-certs ClaudeLauncher.apk >> "%LOG%" 2>&1

echo.>> "%LOG%"
echo BUILD OK : %~dp0ClaudeLauncher.apk>> "%LOG%"
for %%f in (ClaudeLauncher.apk) do echo SIZE=%%~zf bytes>> "%LOG%"
echo BUILD OK

rem ---------- 8. deploy to GitHub ----------
rem git があれば koyomiyusei/claude-launcher に push する。無ければ黙って飛ばす。
echo.>> "%LOG%"
echo [8/8] git push>> "%LOG%"
where git >nul 2>&1
if errorlevel 1 (
  echo git not found - skipped>> "%LOG%"
  goto done
)
if not exist .git (
  git init >> "%LOG%" 2>&1
  git branch -M main >> "%LOG%" 2>&1
  git remote add origin https://github.com/koyomiyusei/claude-launcher.git >> "%LOG%" 2>&1
) else (
  git remote set-url origin https://github.com/koyomiyusei/claude-launcher.git >> "%LOG%" 2>&1
)
git add -A >> "%LOG%" 2>&1
git commit -m "Claude Launcher build" >> "%LOG%" 2>&1
git push -u origin main >> "%LOG%" 2>&1
if errorlevel 1 (
  echo PUSH FAILED>> "%LOG%"
  echo PUSH FAILED - see build.log
) else (
  echo PUSH OK>> "%LOG%"
  echo PUSH OK
)

:done
echo APK: %~dp0ClaudeLauncher.apk
goto end

:fail
echo.>> "%LOG%"
echo BUILD FAILED>> "%LOG%"
echo BUILD FAILED - see build.log

:end
endlocal
