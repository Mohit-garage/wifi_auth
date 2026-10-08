# WiFi Auth

Automatic captive-portal authentication for configured Wi-Fi networks.

WiFi Auth contains:

- A Rust authentication core.
- A Rust CLI.
- An Android application using Rust through UniFFI/JNI.
- Multiple SSID credential profiles.
- Automatic authentication when connecting to a configured Wi-Fi network.
- Encrypted local storage for user credentials.

## Download the Android App

Users can download the latest APK from:

[Download the latest Android release](https://github.com/Mohit-garage/wifi_auth/releases/latest)

No Rust or Android Studio installation is required for users who only want to install the APK.

## Architecture

```text
Android application
        |
        | Kotlin + UniFFI + JNI
        v
Rust authentication core
        |
        | HTTP request to captive portal
        v
Wi-Fi login portal
```

The Android application does not run a separate Rust server. The Rust code is compiled into native Android libraries and embedded inside the APK.

## Repository Structure

```text
wifi_auth/
├── Cargo.toml
├── src/                       Rust source and UniFFI interface
├── android/                   Android Studio project
├── scripts/                   Native build and binding scripts
├── .github/workflows/         Automated APK builds
├── README.md
└── LICENSE
```

## Requirements for Developers

### Rust

- Rust stable
- Cargo
- cargo-ndk

Install cargo-ndk:

```bash
cargo install cargo-ndk
```

### Android

- Android Studio
- Android SDK
- Android NDK
- JDK supported by the Android Gradle Plugin
- Android device or emulator

## Build the Rust CLI

From the repository root:

```bash
cargo check
cargo test
cargo build --release
```

Run the CLI:

```bash
cargo run -- --help
```

## Build Rust for Android

From the repository root:

```powershell
.\scripts\build-android-native.ps1
```

This builds the Android native libraries for:

- `arm64-v8a`
- `x86_64`

The libraries are copied into:

```text
android/app/src/main/jniLibs/
```

## Regenerate Kotlin UniFFI Bindings

Only run this when the Rust UniFFI API changes:

```powershell
.\scripts\build-android-native.ps1
.\scripts\generate-bindings.ps1
```

The generated Kotlin binding should not be edited manually.

## Build the Android Application

```powershell
cd android
.\gradlew.bat clean assembleDebug
```

The APK will be available at:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

Install it using ADB:

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

## Configure the Android App

1. Open the app.
2. Grant Precise Location permission.
3. Grant Nearby Wi-Fi permission if requested.
4. Enable Android Location services.
5. Add a credential profile:
   - Wi-Fi SSID
   - Captive portal login URL
   - User ID
   - Password
6. Connect to the configured Wi-Fi network.

The application selects credentials by the connected SSID and performs authentication automatically.

## Multiple Wi-Fi Profiles

Each saved profile contains:

```text
SSID
Login URL
User ID
Password
```

Different SSIDs can use different credentials.

Example:

```text
Campus-WiFi  → student account
Library-WiFi → library account
Hostel-WiFi  → hostel account
```

The SSID must match the connected Android Wi-Fi name exactly.

## Troubleshooting

Filter Android Logcat using:

```text
WifiAuth
```

Successful authentication should produce messages similar to:

```text
Connected Wi-Fi: Campus-WiFi; saved profile: true
Authenticating profile for SSID: Campus-WiFi
Authentication succeeded
```

If the SSID cannot be detected, verify:

- Wi-Fi is connected.
- Precise Location permission is granted.
- Nearby Wi-Fi permission is granted.
- Android Location services are enabled.
- The app has not been force-stopped.
- Battery optimization is not blocking the app.

## Security

- Credentials are entered by the user at runtime.
- Credentials are stored locally using encrypted Android preferences.
- Never commit real usernames or passwords.
- Never commit keystores or signing passwords.
- Do not upload Logcat files containing private information.
- Do not place credentials in Rust source, Kotlin source, README files, or GitHub Actions logs.

## Releases

Releases are created by pushing a version tag:

```bash
git tag v1.0.0
git push origin v1.0.0
```

GitHub Actions builds the APK and attaches it to the release automatically.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE).