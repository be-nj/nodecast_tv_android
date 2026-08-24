# Changelog

## 1.2.0 (2026-08-24)

- **OIDC/SSO friendly WebView**: d-pad arrow keys are only translated into nodecast-tv key events while the WebView is on the configured server. On foreign pages (e.g. an OIDC identity provider login) the native WebView focus navigation now works, making SSO login forms usable with the remote.
- **Back button fixes**: on foreign pages Back now navigates browser history (falling back to the connection screen); on nodecast pages without the app object (e.g. login.html) Back no longer gets stuck.
- Works with the [Quick Connect](https://github.com/be-nj/nodecast-tv) pairing added to the nodecast-tv fork — no credentials typing on the TV: the login page shows a code + QR, approve it from a signed-in phone.

## 1.1.0 (2026-08-24)

- **Saved servers**: the connection screen now keeps a list of all servers you have connected to. Navigate the list with the d-pad; left/right switches between **Open** and **Delete** on a row, OK executes the selected action.
- **Auto-connect**: optionally reconnect to the last used server automatically on app start (off by default).
- **Settings screen**: reachable from the connection screen — toggle auto-connect and clear the saved server list.
- Android TV installation guide added to the README.

Existing single-server setups are migrated automatically: the previously saved URL becomes the first entry in the server list.

## 1.0.0 (2026-03-20)

- Initial release: connection screen with server URL entry and a WebView wrapper around [nodecast-tv](https://github.com/technomancer702/nodecast-tv) with d-pad remote control support.
