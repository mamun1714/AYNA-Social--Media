# AYNA Android starter

A clean Java + XML Android starter for the AYNA social app.

## Build
GitHub Actions workflow: `.github/workflows/build-apk.yml`.
Run it from the Actions tab using **Build AYNA Debug APK** (or push to `main`).

## Current starter scope
- Android app skeleton
- App label and welcome screen
- Android string resources for English, Bengali, Hindi, Arabic and Urdu
- GitHub Actions debug APK build

## Next implementation stages
1. Create a new Firebase project and register Android package `com.mamun.ayna`.
2. Add the Firebase configuration file and required Gradle plugins/dependencies.
3. Implement authentication, user profiles, feed, posts/media, friends, comments, likes and notifications.
4. Add translated string resources for additional languages and language selection settings.

This starter is not yet a complete social network and does not yet connect to Firebase.
