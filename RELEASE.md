# TITAN PULSE Release Checklist

1. `npm install`
2. `npm run build:web`
3. `npx cap sync android`
4. Verify Java 21 and Android SDK 36 are installed.
5. Run `./gradlew assembleDebug` and `./gradlew assembleRelease` from `android/`.
6. Run `./gradlew bundleRelease` for the Play Store AAB.
7. Run Android 12/13/14/15/16 smoke tests.
8. Verify Notification permission on Android 13+.
9. Verify background build, retry, cancellation and notification deep link.
10. Verify login/register/logout/password reset/account deletion.
11. Verify device-owned data migration after first login.
12. Verify cross-user Firestore access is rejected.
13. Verify API keys never appear in WebView storage or logs.
14. Sign the release build with the owner-controlled production keystore.

Do not mark the release as production-ready until an actual release build completes on a machine with Android SDK 36 and the required Gradle dependencies available.


للمراجعة النهائية: راجع `FINAL_VERIFICATION.md`.
