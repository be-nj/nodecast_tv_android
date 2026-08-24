# Changelog

## 1.1.0 (2026-08-24)

- **Saved servers**: the connection screen now keeps a list of all servers you have connected to. Navigate the list with the d-pad; left/right switches between **Open** and **Delete** on a row, OK executes the selected action.
- **Auto-connect**: optionally reconnect to the last used server automatically on app start (off by default).
- **Settings screen**: reachable from the connection screen — toggle auto-connect and clear the saved server list.
- Android TV installation guide added to the README.

Existing single-server setups are migrated automatically: the previously saved URL becomes the first entry in the server list.

## 1.0.0 (2026-03-20)

- Initial release: connection screen with server URL entry and a WebView wrapper around [nodecast-tv](https://github.com/technomancer702/nodecast-tv) with d-pad remote control support.
