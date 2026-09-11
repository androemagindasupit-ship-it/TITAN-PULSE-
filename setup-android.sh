#!/usr/bin/env bash
set -euo pipefail
command -v node >/dev/null || { echo 'Node.js مطلوب'; exit 1; }
command -v npm >/dev/null || { echo 'npm مطلوب'; exit 1; }
npm install
npm run test:static
npx cap sync android
echo 'تم تجهيز Android. افتح android في Android Studio.'
