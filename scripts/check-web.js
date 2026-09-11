const fs = require('fs');
const html = fs.readFileSync('www/index.html','utf8');
if (!html.includes('<html') || !html.includes('</html>')) throw new Error('index.html is incomplete');
for (const token of ['APP_ICON_32','APP_ICON_192','APP_ICON_512','nativeBridgeCall','saveSessionState']) {
  if (!html.includes(token)) throw new Error(`Missing web token: ${token}`);
}
const scripts = html.match(/<script\b[^>]*>/gi) || [];
if (scripts.length < 3) throw new Error('Unexpectedly low script count');
if (!html.includes('sandbox="allow-scripts allow-popups"')) throw new Error('preview sandbox hardening missing');
if (!html.includes('frame-ancestors')) throw new Error('preview CSP hardening missing');
console.log(`TITAN PULSE web check OK: ${html.length} bytes, ${scripts.length} script tags.`);
