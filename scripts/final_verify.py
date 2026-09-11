#!/usr/bin/env python3
import hashlib, json, re, sys, zipfile, xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
errors=[]; warnings=[]
def need(path):
    p=ROOT/path
    if not p.exists(): errors.append(f"missing: {path}")
    return p
for rel in [
    'android/gradlew','android/gradlew.bat','android/gradle/wrapper/gradle-wrapper.jar',
    'android/app/src/main/java/com/titanpulse/app/MainActivity.java',
    'android/app/src/main/java/com/titanpulse/app/TitanPulseBridge.java',
    'android/app/src/main/java/com/titanpulse/app/BackgroundBuildWorker.java',
    'android/app/src/main/java/com/titanpulse/app/sync/BackgroundSyncWorker.java',
    'android/app/src/main/java/com/titanpulse/app/sync/FirebaseSyncService.java',
    'android/app/src/main/java/com/titanpulse/app/data/TitanDao.java',
    'android/app/src/main/java/com/titanpulse/app/data/TitanDatabase.java',
    'android/app/src/main/java/com/titanpulse/app/security/SecureKeyStore.java',
    'android/app/src/main/AndroidManifest.xml','www/index.html','firebase/firestore.rules']:
    need(Path(rel))
html=(ROOT/'www/index.html').read_text(errors='ignore')
bridge=(ROOT/'android/app/src/main/java/com/titanpulse/app/TitanPulseBridge.java').read_text(errors='ignore')
worker=(ROOT/'android/app/src/main/java/com/titanpulse/app/BackgroundBuildWorker.java').read_text(errors='ignore')
sync=(ROOT/'android/app/src/main/java/com/titanpulse/app/sync/FirebaseSyncService.java').read_text(errors='ignore')
dao=(ROOT/'android/app/src/main/java/com/titanpulse/app/data/TitanDao.java').read_text(errors='ignore')
main=(ROOT/'android/app/src/main/java/com/titanpulse/app/MainActivity.java').read_text(errors='ignore')
manifest=(ROOT/'android/app/src/main/AndroidManifest.xml').read_text(errors='ignore')
asserts=[
('bridge token validation','requirePayload' in bridge and 'bridgeToken' in bridge),
('native key never exposed','getProviderSecret(String ignored) { return "[]"; }' in bridge),
('AI registry','AiProviderRegistry.get(providerId)' in worker),
('background job runtime scope','callWithFallback(job, providers, startedNanos)' in worker),
('full sync pagination','FieldPath.documentId()' in sync and 'startAfter(last)' in sync),
('Room workspace-project query','getProjectsForOwnerAndWorkspace' in dao),
('predictive back','OnBackPressedDispatcher' in main),
('web navigation allowlist','localhost' in main and 'shouldOverrideUrlLoading' in main),
('strict preview CSP','frame-ancestors' in html and 'base-uri' in html and 'object-src' in html),
('preview sandbox','sandbox="allow-scripts allow-popups"' in html),
('cloud account functions', all(f'function {x}' in html for x in ['cloudLogin','cloudRegister','cloudSyncNow','cloudLogout','cloudDeleteAccount'])),
('firebase transactions','runTransaction' in sync),
('no cleartext','usesCleartextTraffic="false"' in manifest),
]
for name,ok in asserts:
    if not ok: errors.append(name)
try: ET.parse(ROOT/'android/app/src/main/AndroidManifest.xml')
except Exception as e: errors.append(f'AndroidManifest XML: {e}')
for xml in (ROOT/'android/app/src/main/res/xml').glob('*.xml'):
    try: ET.parse(xml)
    except Exception as e: errors.append(f'{xml.name} XML: {e}')
# Scan for direct API secret exposure patterns in web layer.
if re.search(r'getProviderSecret\s*\(', html): errors.append('web layer still attempts plaintext getProviderSecret')
if re.search(r'requestAi[\'\"]\s*,\s*JSON\.stringify\(\{[^}]{0,1000}baseURL', html, re.I): errors.append('web requestAi includes baseURL payload')
# Check duplicate native sources.
if (ROOT/'native').exists(): errors.append('stale native/ source tree present')
# Check gradle wrapper jar header is non-empty.
jar=ROOT/'android/gradle/wrapper/gradle-wrapper.jar'
if jar.exists() and jar.stat().st_size < 10000: warnings.append('wrapper jar unusually small; build environment should verify it')
print('FINAL STATIC VERIFICATION')
print('PASS' if not errors else 'FAIL')
for e in errors: print('ERROR:',e)
for w in warnings: print('WARN:',w)
if errors: sys.exit(1)
# Deterministic project digest for release notes.
h=hashlib.sha256()
for p in sorted(ROOT.rglob('*')):
    if p.is_file() and '.git' not in p.parts and 'node_modules' not in p.parts:
        h.update(str(p.relative_to(ROOT)).encode()); h.update(p.read_bytes())
print('Project SHA-256:',h.hexdigest())
