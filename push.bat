@echo off
rem Claude Launcher -> GitHub (koyomiyusei/claude-launcher)
setlocal
cd /d "%~dp0"
set "LOG=%~dp0push.log"
echo === push %DATE% %TIME% > "%LOG%"

where git >nul 2>&1
if errorlevel 1 (
  echo ERROR: git not found on PATH >> "%LOG%"
  echo git が見つかりません
  goto end
)

if not exist .git (
  git init >> "%LOG%" 2>&1
  git branch -M main >> "%LOG%" 2>&1
  git remote add origin https://github.com/koyomiyusei/claude-launcher.git >> "%LOG%" 2>&1
) else (
  git remote set-url origin https://github.com/koyomiyusei/claude-launcher.git >> "%LOG%" 2>&1
)

git add -A >> "%LOG%" 2>&1
git commit -m "Claude Launcher v1.1" >> "%LOG%" 2>&1
git push -u origin main >> "%LOG%" 2>&1
if errorlevel 1 (
  echo PUSH FAILED >> "%LOG%"
  echo push に失敗しました。push.log を確認してください
  goto end
)
echo PUSH OK >> "%LOG%"
echo PUSH OK

:end
endlocal
