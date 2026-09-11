# TITAN PULSE — Android Production Candidate

هذه الحزمة مبنية على Capacitor 8 مع Android SDK 36 وJava 21، وتحافظ على واجهة TITAN PULSE الحالية.

## أهم التحسينات

- Secure API keys داخل Android Keystore.
- Native AI provider allowlist.
- Room database + ownership + migrations.
- Firebase Auth + transactional project sync.
- WorkManager للمهام الخلفية.
- Native notifications + deep links.
- WebView origin hardening + preview sandbox.
- Account migration وForgot Password وDelete Account.

## بناء التطبيق

راجع `BUILD.md` و`RELEASE.md`.

> ملاحظة: لم يتم وضع `node_modules` داخل ZIP. يتم تنزيل التبعيات في أول `npm install`.


للمراجعة النهائية: راجع `FINAL_VERIFICATION.md`.
