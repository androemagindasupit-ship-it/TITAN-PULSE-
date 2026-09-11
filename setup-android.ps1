$ErrorActionPreference = 'Stop'
Write-Host 'TITAN PULSE - Capacitor Android setup' -ForegroundColor Cyan
if (-not (Get-Command node -ErrorAction SilentlyContinue)) { throw 'Node.js غير مثبت. ثبّت Node.js 22 LTS أو أحدث ثم أعد التشغيل.' }
if (-not (Get-Command npm -ErrorAction SilentlyContinue)) { throw 'npm غير مثبت.' }
Write-Host '1) تثبيت حزم Capacitor...' -ForegroundColor Yellow
npm install
Write-Host '2) فحص ملفات الويب...' -ForegroundColor Yellow
npm run test:static
Write-Host '3) مزامنة Capacitor مع Android...' -ForegroundColor Yellow
npx cap sync android
Write-Host 'تم. افتح مجلد android في Android Studio ثم Build > Build APK(s).' -ForegroundColor Green
