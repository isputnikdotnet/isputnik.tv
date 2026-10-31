# iSputnik TV

The native app for Android TV and Fire TV: the front door to a member's iSputnik on a
television. The plan and its reasons are in `isputnik.home/docs/tv-app-plan.md`.

It signs in through home's Link a device and calls the routes the website calls, so every
permission stays on the server. It holds no accounts or media of its own.

| Module | Holds |
|---|---|
| `core` | The API client with its session, the server address, theme |
| `player` | Media3, and the rule for falling back to the server's converted copy |
| `feature-video` | Rows, a show's episodes, the player screen |
| `app` | Connect and link screens, and what puts the rest together |

## Building

Needs Android Studio's JDK and the Android SDK. From Git Bash:

```bash
export JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
./gradlew testDebugUnitTest assembleRelease --console=plain
```

Without the real key this signs with the debug key, which is fine for trying a build on a TV.

## Releasing

1. Set `tvVersion` in `gradle.properties`.
2. Commit, then push an annotated tag `v<version>`. Its message becomes the release notes.
3. `.github/workflows/release.yml` tests and builds, checks the APK is signed with the real key,
   and publishes it as a GitHub Release. TVs see it the next time the app opens.

The signing key is in the repo's secrets (`TV_SIGNING_KEYSTORE`, base64, and
`TV_SIGNING_PASSWORD`) and in an offline backup. **If it's lost, no TV can take an update:**
each has to uninstall the app and link everyone again.

To build a release by hand, name the key in `local.properties`: `signing.store` and
`signing.password`.

## Trying it on a TV

Turn on ADB debugging on the TV, then:

```bash
adb connect <tv address>:5555
adb install -r app/build/outputs/apk/release/app-release.apk
```

A 1080p TV is 960 × 540 dp, so every screen has to fit in that.
