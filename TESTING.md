# TITAN PULSE Testing

## Static checks completed

- Web structure check
- Production source-policy check
- Inline JavaScript syntax check
- XML well-formedness check
- Provider allowlist verification
- Secret-return regression check
- Cloud function presence check
- Gradle wrapper presence check
- Stale duplicate Native source removal check

## Required machine tests before release

Run on a development machine with Java 21, Android SDK 36 and network access:

```bash
npm install
npm run test:static
npx cap sync android
cd android
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew bundleRelease
```

Then exercise Android 12, 13, 14, 15 and 16 for:

- Login/register/logout
- Password reset
- Account deletion
- Local save/restore
- Device-owned data migration
- Project sync and conflict
- AI request through Native bridge
- Background build
- Cancellation/retry
- Notification and deep link
- Offline/online transition
- App restart after background job
- Upgrade from previous production database schema

The final production sign-off requires a real release build and device/emulator results recorded in the release test matrix.


للمراجعة النهائية: راجع `FINAL_VERIFICATION.md`.
