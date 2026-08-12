# KUAS Cafeteria

Android app for checking the daily cafeteria menu at KUAS. Shows what's on the menu for each campus, lets you filter out stuff you're allergic to, keep a running total of what you picked ("tray"), and compare a couple of items side by side. There are also three home screen widgets (today's menu, cafeteria open/closed status, and highlights).

## Why this exists

Built as part of an ID3 coursework project. The idea started from just wanting to know what's for lunch without opening the school website every time, so this pulls the same menu data through a small API and presents it as a native app instead.

## Features

- Day view with menu items grouped by category, swipe between days
- Allergen filter (bottom sheet) so items you can't eat get hidden or flagged
- Item detail screen with full info per dish
- Tray — add items you're planning to get and see a running list
- Compare two items at once
- Onboarding flow on first launch (pick language, campus, allergens)
- Settings screen
- Home screen widgets: Today, Status, Highlights, each with its own config screen
- Dark theme support
- Localized into English, Japanese, Korean, Chinese, French, Spanish and Russian
- Basic analytics/crash logging hooks

## Stack

- Kotlin, XML views (no Compose)
- MVVM-ish: Fragments + ViewModels + LiveData
- Retrofit + OkHttp + Gson for the network layer
- DataStore for storing user preferences
- Navigation Component for screen flow
- Disk cache for menu data so the app still shows something offline

Min SDK 30, target SDK 36.

## Backend

The app talks to a small self-hosted API that scrapes/parses the cafeteria menu and serves it as JSON. That backend lives in a separate repo, this one is just the Android client.

## Building

Standard Android Studio project, nothing unusual.

```
./gradlew assembleDebug
```

Release builds need signing config values (store file, passwords, key alias) in a local `local.properties` — that file is not included here since it holds the actual signing keys, you'd need your own if you want to build a signed release.

## Status

Working, used personally on a daily basis. Some parts (like the allergen list) are hardcoded for now rather than pulled from the API.
