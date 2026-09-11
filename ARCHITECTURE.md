# TITAN PULSE Production Architecture

## Runtime

Capacitor 8 Android shell + local web application. Android-native responsibilities are isolated from the web UI.

## Native layers

- `MainActivity`: WebView hardening, local-origin navigation allowlist, lifecycle and Android back handling.
- `TitanPulseBridge`: narrow JavaScript bridge, account-scoped local storage, secure secret metadata, notifications, account actions and async callbacks.
- `AiProviderRegistry`: audited native provider allowlist and fixed HTTPS endpoints.
- `BackgroundBuildWorker`: durable AI build jobs using WorkManager, retries and notifications.
- `BackgroundSyncWorker`: owner-scoped project synchronization.
- `FirebaseSyncService`: Firebase Auth + transactional project synchronization.
- `SecureKeyStore`: account-scoped AES-GCM storage protected by Android Keystore.
- Room: local persistent database for projects, workspaces, jobs, drafts and metadata.

## Data ownership

Local entities that can contain user data are associated with an owner ID. On first successful authentication, device-owned local data and legacy secret storage are migrated to the Firebase UID.

## Sync conflict model

Project synchronization uses Firestore transactions and revision/device tie-breakers. Server timestamps are preferred over device clock values when canonicalizing the local record.

## Preview isolation

Generated project previews are sandboxed. They must never receive `allow-same-origin`, and they must never be loaded into the main app document.


للمراجعة النهائية: راجع `FINAL_VERIFICATION.md`.
