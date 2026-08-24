# NodecastTV

An android app wrapping the [nodecast-tv](https://github.com/technomancer702/nodecast-tv) project.

[nodecast-tv](https://github.com/technomancer702/nodecast-tv) is a web app for viewing IPTV in a browser.

As browser support is limited in Google TV / Android TV, this app is designed to work on a TV, allowing you to navigate the app using a standard TV remote control.

## Latest release

Get [latest release](https://github.com/jackduckworth2/nodecast_tv_android/releases/latest)

## Building it yourself

### Getting Started with flutter

This project is a starting point for a Flutter application.

A few resources to get you started if this is your first Flutter project:

- [Learn Flutter](https://docs.flutter.dev/get-started/learn-flutter)
- [Write your first Flutter app](https://docs.flutter.dev/get-started/codelab)
- [Flutter learning resources](https://docs.flutter.dev/reference/learning-resources)

For help getting started with Flutter development, view the
[online documentation](https://docs.flutter.dev/), which offers tutorials,
samples, guidance on mobile development, and a full API reference.

### Building

```
git clone https://github.com/jackduckworth2/nodecast_tv_android.git
cd nodecast_tv_android
flutter pub get
flutter build apk --debug
```

## Installing on your Android TV

You need the APK file first — either download it from the [latest release](https://github.com/jackduckworth2/nodecast_tv_android/releases/latest) or build it yourself (see above; the result is at `build/app/outputs/flutter-apk/app-release.apk`).

### Option A: Install via ADB (recommended)

1.  On your TV, enable Developer options: go to **Settings → System → About**, scroll to **Android TV OS build** (or **Build**) and click it 7 times.
2.  Go to **Settings → System → Developer options** and enable **USB debugging** / **Network debugging** (name varies by device; on Google TV it is under **Developer options → Wireless debugging** or **ADB debugging**).
3.  Find your TV's IP address under **Settings → Network & Internet → (your network)**.
4.  From your computer (needs [ADB / platform-tools](https://developer.android.com/tools/releases/platform-tools) installed and the TV on the same network):

    ```
    adb connect <TV_IP>:5555
    adb install app-release.apk
    ```

    Accept the debugging prompt that appears on the TV the first time you connect.
5.  The app appears in your TV's app list as **NodecastTV**. On Google TV it may be hidden under **Apps → See all apps**.

### Option B: Install with the "Downloader" app (no computer needed)

1.  Install **Downloader by AFTVnews** from the Play Store on your TV.
2.  Allow it to install unknown apps: **Settings → Apps → Security & Restrictions → Unknown sources** (or **Settings → Privacy → Security & Restrictions**) and enable it for Downloader.
3.  Open Downloader and enter the direct URL of the APK (e.g. the `app-release.apk` asset URL from the [releases page](https://github.com/jackduckworth2/nodecast_tv_android/releases/latest), or a URL where you host your self-built APK).
4.  When the download finishes, choose **Install**.

### First start

1.  Make sure your nodecast-tv server is running and reachable from the TV (see "Running nodecast" below).
2.  Open NodecastTV on the TV and enter the server URL (e.g. `http://192.168.1.50:5173`) on the connection screen.

### Updating

Install the new APK over the old one the same way (`adb install -r app-release.apk` with ADB). Note: a self-built APK and a release APK are signed with different keys — switching between them requires uninstalling first, which clears the saved server URL.

## Running nodecast

Unless and until these nodecast-tv changes are merged into the main branch, you will have to use the forked version

1.  Docker
    ```
    nodecast-tv:
      build: https://github.com/jackduckworth2/nodecast-tv
    ```

2.  Manual install
    ```
    git clone https://github.com/jackduckworth2/nodecast-tv
    cd nodecast-tv
    npm install
    npm run dev
    ```

## Implementation

1.  Updated nodecast-tv code to [respond to d-pad presses](https://github.com/jackduckworth2/nodecast-tv)

2.   Created simple android app with
     - connection screen allowing you to enter the nodecast-tv url
     - saved server list - every successfully opened server is remembered; open or delete entries with the remote (left/right switches between __Open__ and __Delete__, OK executes)
     - settings screen with an auto-connect toggle (reconnects to the last used server on app start) and a way to clear the saved servers
     - single WebView component with browser output

3.  Tips
    - hit the __Back__ button if you cant find focus - it should take you back to __Home__
    - hit the __Back__ button when on __Home__ to go back the Connection screen

See [CHANGELOG.md](CHANGELOG.md) for version history.
