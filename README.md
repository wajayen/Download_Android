# Download Android

Android port of the Windows downloader.

Current Android version: `0.29.0` (`versionCode 29`).

## Build

```powershell
C:\antigravity\downloader_android\build_android_apk.ps1
```

The debug APK is copied to:

```text
C:\antigravity\downloader_android\dist
```

## GitHub Sync Rule

This project follows the Windows downloader release rule:

- Build every local Android version normally.
- When `versionCode % 10 == 0`, `build_android_apk.ps1` calls `publish_github.ps1`.
- The publish script commits tracked source changes, pushes to GitHub, tags `v<versionCode>`, and uploads the versioned APK as a GitHub release asset.
- Non-sync versions print a message and do not publish automatically.

Examples:

- `versionCode 30` syncs to GitHub as tag `v30`.
- `versionCode 31` through `39` build locally only.
- `versionCode 40` syncs again as tag `v40`.

## Status

See [ANDROID_PORT_STATUS.md](ANDROID_PORT_STATUS.md) for the current porting coverage and known desktop parity gaps.

