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

Needs Android Studio's JDK and the Android SDK. There's no `gradlew` script in the repo yet,
so from Git Bash:

```bash
export JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
"$JAVA_HOME/bin/java" -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain testDebugUnitTest assembleRelease
```

The release build is signed with the debug key until there's a real one (T4).

## Trying it on a TV

Turn on ADB debugging on the TV, then:

```bash
adb connect <tv address>:5555
adb install -r app/build/outputs/apk/release/app-release.apk
```

A 1080p TV is 960 × 540 dp, so every screen has to fit in that.
