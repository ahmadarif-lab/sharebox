#!/usr/bin/env node
// Unggah store/data_safety/answers.csv ke Play (applications.dataSafety). node store/tools/play_data_safety.mjs
import fs from 'node:fs';
import crypto from 'node:crypto';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const ROOT = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const PKG = 'id.ahmadarif.sharebox';
const k = JSON.parse(fs.readFileSync(process.env.HOME + '/.config/play-console/service-account.json'));
const b64 = (o) => Buffer.from(JSON.stringify(o)).toString('base64url');
const now = Math.floor(Date.now() / 1000);
const u = b64({ alg: 'RS256', typ: 'JWT' }) + '.' + b64({ iss: k.client_email, scope: 'https://www.googleapis.com/auth/androidpublisher', aud: 'https://oauth2.googleapis.com/token', iat: now, exp: now + 3600 });
const sig = crypto.createSign('RSA-SHA256').update(u).sign(k.private_key, 'base64url');
const t = await (await fetch('https://oauth2.googleapis.com/token', { method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion: u + '.' + sig }) })).json();
const r = await fetch(`https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${PKG}/dataSafety`, { method: 'POST', headers: { authorization: 'Bearer ' + t.access_token, 'content-type': 'application/json' }, body: JSON.stringify({ safetyLabels: fs.readFileSync(path.join(ROOT, 'store/data_safety/answers.csv'), 'utf8') }) });
console.log(r.status, (await r.text()).slice(0, 600));
