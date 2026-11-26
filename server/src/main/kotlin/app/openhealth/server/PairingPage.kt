package app.openhealth.server

/**
 * The one page a person opens in a browser to sign in with a passkey and receive a
 * one-time pairing code. It is deliberately self-contained: no font, script or image
 * comes from anywhere else, so the page works on a LAN with no internet and leaks
 * nothing (`pr.md` §6.23).
 */
@Suppress("LongMethod") // one self-contained HTML document, no external asset
fun pairingPage(config: ServerConfig): String {
    val rp = config.domain
    return """
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta http-equiv="Content-Security-Policy"
      content="default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'self'">
<title>openHealth sync</title>
<style>
 :root { color-scheme: dark; }
 body { margin: 0; background: #111214; color: #eceff4;
        font: 16px/1.5 system-ui, -apple-system, Segoe UI, sans-serif; }
 main { max-width: 30rem; margin: 8vh auto; padding: 0 1.25rem; }
 h1 { font-size: 1.6rem; font-weight: 600; margin: 0 0 .25rem; }
 p { color: #b8bdc7; }
 label { display: block; margin: 1.25rem 0 .4rem; color: #b8bdc7; }
 input { width: 100%; padding: .75rem; border-radius: .6rem; border: 1px solid #343841;
         background: #1a1c20; color: #eceff4; font-size: 1rem; box-sizing: border-box; }
 button { margin-top: 1rem; width: 100%; padding: .8rem; border-radius: .6rem; border: 0;
          background: #ffd60a; color: #111214; font-size: 1rem; font-weight: 600; cursor: pointer; }
 button.secondary { background: #2a2d34; color: #eceff4; }
 code { display: block; margin-top: 1rem; padding: 1rem; border-radius: .6rem;
        background: #1a1c20; border: 1px solid #343841;
        font: 700 1.5rem/1.2 ui-monospace, SFMono-Regular, Menlo, monospace;
        letter-spacing: .18em; text-align: center; }
 .error { color: #ffb4ab; margin-top: 1rem; min-height: 1.5rem; }
 .note { font-size: .875rem; color: #8b919c; margin-top: 1.5rem; }
</style>
</head>
<body>
<main>
  <h1>openHealth sync</h1>
  <p>Sign in with the passkey on this device, then enter the code in the app.</p>
  <label for="username">Username</label>
  <input id="username" autocomplete="username" value="owner">
  <button id="signin">Sign in with a passkey</button>
  <button id="register" class="secondary">Create the first passkey</button>
  <div id="error" class="error" role="alert"></div>
  <div id="result" hidden>
    <p>Enter this code in the app under Settings → Sync. It works once, for ten minutes.</p>
    <code id="code"></code>
  </div>
  <p class="note">$rp · passkeys only · no analytics, no external requests</p>
</main>
<script>
const b64 = {
  toBuf(s) {
    const pad = '='.repeat((4 - s.length % 4) % 4);
    const raw = atob((s + pad).replace(/-/g, '+').replace(/_/g, '/'));
    return Uint8Array.from(raw, c => c.charCodeAt(0));
  },
  fromBuf(buf) {
    return btoa(String.fromCharCode(...new Uint8Array(buf)))
      .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  }
};

function decodeOptions(options) {
  options.challenge = b64.toBuf(options.challenge);
  if (options.user) options.user.id = b64.toBuf(options.user.id);
  if (options.allowCredentials) {
    options.allowCredentials = options.allowCredentials.map(c => ({...c, id: b64.toBuf(c.id)}));
  }
  return options;
}

function encodeCredential(credential) {
  const r = credential.response;
  const response = r.attestationObject
    ? {
        clientDataJSON: b64.fromBuf(r.clientDataJSON),
        attestationObject: b64.fromBuf(r.attestationObject),
        transports: r.getTransports ? r.getTransports() : []
      }
    : {
        clientDataJSON: b64.fromBuf(r.clientDataJSON),
        authenticatorData: b64.fromBuf(r.authenticatorData),
        signature: b64.fromBuf(r.signature),
        userHandle: r.userHandle ? b64.fromBuf(r.userHandle) : null
      };
  return {
    id: credential.id,
    rawId: b64.fromBuf(credential.rawId),
    type: credential.type,
    response,
    clientExtensionResults: credential.getClientExtensionResults()
  };
}

async function post(path, body, token) {
  const headers = {'content-type': 'application/json'};
  if (token) headers['authorization'] = 'Bearer ' + token;
  const res = await fetch(path, {method: 'POST', headers, body: JSON.stringify(body)});
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.error || ('request failed: ' + res.status));
  return data;
}

const $ = id => document.getElementById(id);
let session = null;

async function showCode() {
  const pairing = await post('/pair/code', {}, session);
  $('code').textContent = pairing.code;
  $('result').hidden = false;
  $('error').textContent = '';
}

$('signin').onclick = async () => {
  try {
    const username = $('username').value.trim();
    const options = await post('/auth/login/options', {username});
    const publicKey = decodeOptions(options.options);
    const credential = await navigator.credentials.get({publicKey});
    const result = await post('/auth/login/verify', {
      username, challengeId: options.challengeId, response: JSON.stringify(encodeCredential(credential))
    });
    session = result.token;
    await showCode();
  } catch (e) {
    $('error').textContent = e.message;
  }
};

$('register').onclick = async () => {
  try {
    const username = $('username').value.trim();
    const options = await post('/auth/register/options', {username, displayName: username});
    const publicKey = decodeOptions(options.options);
    const credential = await navigator.credentials.create({publicKey});
    const result = await post('/auth/register/verify', {
      username, challengeId: options.challengeId, response: JSON.stringify(encodeCredential(credential))
    });
    session = result.token;
    await showCode();
  } catch (e) {
    $('error').textContent = e.message;
  }
};
</script>
</body>
</html>
    """.trimIndent()
}
