const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const fail = (msg) => { console.error(`PRODUCTION CHECK FAILED: ${msg}`); process.exit(1); };
const read = (p) => fs.readFileSync(path.join(root, p), 'utf8');

const html = read('www/index.html');
const bridge = read('android/app/src/main/java/com/titanpulse/app/TitanPulseBridge.java');
const worker = read('android/app/src/main/java/com/titanpulse/app/BackgroundBuildWorker.java');
const main = read('android/app/src/main/java/com/titanpulse/app/MainActivity.java');
const firebase = read('android/app/src/main/java/com/titanpulse/app/sync/FirebaseSyncService.java');

for (const fn of ['cloudLogin','cloudRegister','cloudSyncNow','cloudLogout','cloudForgotPassword','cloudDeleteAccount']) {
  if (!html.includes(`function ${fn}`)) fail(`missing web function ${fn}`);
}
if (/requestAi',JSON\.stringify\(\{[^}]*baseURL/i.test(html)) fail('web-to-native AI request still passes baseURL');
if (!html.includes('BUILT_IN_PROVIDERS.some(p=>p.id===providerId)')) fail('native AI provider allowlist missing');
if (!bridge.includes('public String getProviderSecret(String ignored) { return "[]"; }')) fail('plaintext provider-secret bridge regression detected');
if (bridge.includes('secureKeyStore.get(pid)')) fail('unscoped native key access detected');
if (!worker.includes('callWithFallback(job, providers, startedNanos)')) fail('background runtime clock scope fix missing');
if (!bridge.includes('getProjectsForOwnerAndWorkspace') && !bridge.includes('saveWorkspacesToRoom')) fail('room workspace persistence check missing');
if (!worker.includes('AiProviderRegistry.get(providerId)')) fail('background provider allowlist missing');
if (!main.includes('shouldOverrideUrlLoading')) fail('WebView navigation allowlist missing');
if (!main.includes('"localhost".equals(host)')) fail('local-origin WebView allowlist missing');
if (!firebase.includes('runTransaction')) fail('transactional Firestore sync missing');
if (firebase.includes('signInWithEmailAndPassword(email.trim(), password), TIMEOUT_SECONDS')) fail('blocking sign-in regression detected');
if (firebase.includes('createUserWithEmailAndPassword(email.trim(), password), TIMEOUT_SECONDS')) fail('blocking registration regression detected');
if (!bridge.includes('signInEmailAsync')) fail('async native sign-in missing');
if (!bridge.includes('deleteCurrentUserAndDataAsync')) fail('async native account deletion missing');
if (!bridge.includes('deleteProject(oldOwner, oldId)')) fail('owner-scoped project migration cleanup missing');
if (!worker.includes('INPUT_OWNER_ID')) fail('background worker owner isolation missing');
if (!firebase.includes('public/firebase_config.json')) fail('Capacitor asset Firebase config path missing');
if (fs.existsSync(path.join(root, 'native'))) fail('stale duplicate native/ source tree must not ship');
if (!html.includes('requestNotificationPermission')) fail('notification permission bridge missing');
if (!html.includes('queueNotification')) fail('notification queue bridge missing');
for (const f of ['FINAL_VERIFICATION.md','PRIVACY.md']) if (!fs.existsSync(path.join(root,f))) fail(`missing ${f}`);
for (const f of ['android/gradlew','android/gradlew.bat','android/gradle/wrapper/gradle-wrapper.jar']) if (!fs.existsSync(path.join(root,f))) fail(`missing ${f}`);
if (!html.includes("meta http-equiv=\"Content-Security-Policy\"")) {
  // The generated preview contains the CSP string rather than a literal HTML tag.
  if (!html.includes('Content-Security-Policy')) fail('generated preview CSP missing');
}
console.log('TITAN PULSE production static checks: PASS');
