# TITAN PULSE Security Notes

- Provider API keys on Android are encrypted with AES-GCM and the key material is protected by Android Keystore.
- Native AI requests use a fixed provider allowlist; JavaScript does not receive plaintext provider keys and cannot choose an arbitrary endpoint for a native secret-backed request.
- The WebView accepts navigation only to the Capacitor local origin. External HTTP(S) links are opened outside the app WebView.
- AI project previews run in sandboxed iframes without `allow-same-origin`, so preview code cannot access the Android bridge.
- Firebase project documents are scoped to the authenticated user's UID and Firestore rules reject cross-user access and unexpected project fields.
- API keys are never uploaded to Firestore by the native sync layer.
- Secure-key preferences are excluded from Android backup/device transfer.
- Release builds must keep `debuggable=false` and must never include production secrets in source control.

## Important operational requirements

The production Firebase project must use the rules in `firebase/firestore.rules`, authentication must be configured, and the production signing key must remain owned by the app owner.

A custom AI provider is intentionally not allowed to use the native secret-backed path unless it is added to the audited native provider registry.


للمراجعة النهائية: راجع `FINAL_VERIFICATION.md`.
