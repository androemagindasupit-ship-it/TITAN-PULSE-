# TITAN PULSE Privacy Policy Draft

This document is a technical draft and must be reviewed and published by the app owner as the final legal privacy policy before public distribution.

## Data stored locally

Projects, drafts, settings and job metadata can be stored on the device to provide offline persistence and recovery.

Provider API keys entered in the Android app are stored in encrypted native storage protected by Android Keystore. The plaintext keys are not returned to JavaScript.

## Cloud synchronization

When the user signs in and uses cloud synchronization, project data is stored in the owner's Firebase account namespace. API keys are not synchronized to Firestore by the native sync layer.

## AI providers

Prompts and project context may be sent to the AI provider selected by the user. The app owner must disclose the applicable providers and their policies in the final public privacy notice.

## Account deletion

The Android app provides an account deletion action. The current implementation deletes the authenticated user's project documents and then requests deletion of the Firebase Authentication account. Provider-specific external data retention remains subject to the provider's own policies.

## Crash diagnostics

If production crash monitoring is enabled, diagnostics must be configured without API keys, passwords or private project contents.
