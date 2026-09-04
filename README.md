# Nova Android Assistant

Nova is a native Kotlin Android app that listens for short voice commands and
opens installed Android apps with Android intents.

## Build in Android Studio

1. Open the `nova-android` folder in Android Studio.
2. Allow Android Studio to install the Android SDK platform/build tools for API
   35 if prompted.
3. Connect an Android phone or start an emulator running Android 8.0 (API 26)
   or newer.
4. Build and run the `app` configuration.

The project uses Gradle 8.9, Android Gradle Plugin 8.7.3, Kotlin 2.0.21, and
Java 17.

For a command-line debug APK after installing Android SDK/Java 17:

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Build the APK from a phone with GitHub Actions

This project includes `.github/workflows/build-apk.yml`, so Android Studio is
not required:

1. Create a new GitHub repository from your phone.
2. Upload the **contents of this `nova-android` folder** to the repository
   root. Make sure `.github/workflows/build-apk.yml` is uploaded too.
3. Open the repository's **Actions** tab.
4. Select **Build Nova APK**.
5. Press **Run workflow**, select the `main` branch, and press the green
   **Run workflow** button.
6. Wait for the workflow to finish with a green check.
7. Open that completed workflow run, scroll to **Artifacts**, and tap
   `nova-debug-apk` to download the ZIP.
8. Extract the ZIP on the phone and install `app-debug.apk`. Android may ask
   you to allow installation from that browser/file manager.

The workflow installs Java, Gradle's Android requirements, and Android API 35
on GitHub's build machine before producing the debug APK.

## Using Nova

1. Open Nova and tap **Start listening**.
2. Grant microphone permission. Android 13+ also asks for notification
   permission so the active microphone notification can remain visible.
3. With the wake phrase toggle enabled, say:
   - “Hey Nova, open YouTube”
   - “Hey Nova, open Chrome”
   - “Hey Nova, open Free Fire”
   - “Hey Nova, open Camera”
4. Turn off **Require “Hey Nova” wake phrase** to use “open YouTube” directly.
5. Stop listening from the app or from the persistent notification.

Nova first tries on-device speech recognition when the device exposes it, and
requests offline recognition from the Android speech service. Availability and
offline behavior still depend on the device's installed speech service and
language pack; Nova does not send commands to a custom cloud backend.

## Android microphone limitation

Nova uses a foreground service with the `microphone` foreground-service type
and an ongoing notification. This is the reliable Android-supported pattern
for continuing a user-started listening session after the app screen is
covered.

A normal third-party app cannot promise a Google-Assistant-style, invisible,
24/7 hotword listener. Android can stop background processes, restrict
background activity launches, and require the user to start microphone access
while the app is visible. Nova therefore starts listening after an explicit
tap and continuously re-arms short Android speech-recognition sessions while
the foreground service remains alive. If the OS or the speech service stops
the service, the user must tap **Start listening** again.