# Forklore

A private, shared record of restaurants and the dishes worth remembering.

Forklore replaces the note-app habit of keeping restaurants in a wall of text: where
you've been, where you want to go, what you ordered, what was actually good, and what
to never order again. It's built around the thing generic review apps miss — **the dish,
not just the restaurant** — and around the fact that the people you eat with disagree
with you and their opinions are worth keeping too.

## Why

This started as a real text file. It looked like this (names invented,
structure verbatim):

```
Cafe Mirabel - what we got 7/31/26
Arancini (bad) ((Sam says they were "fine")) and bread service
lamb meatballs and they were PHENOMENAL delish, white sauce FIYA
Get salt with the table. Skip bread and arancini.
What we want- burrata, pan con tomate, patatas bravas
```

Every feature in this app exists because that file needed it: per-person opinions on the
same dish, a want-to-try list per restaurant, an explicit *never again*, fuzzy dates,
and free text that never gets in your way.

## Principles

- **Offline-first.** The app is fully usable with no network and no account. Sign-in
  exists only to sync and to share lists with someone else.
- **Free text is first-class.** Structure is an optional layer over a note, never a gate
  in front of one. If capturing a thought is slower here than in a notes app, this app
  has failed.
- **Your data is yours.** Place lookup is pluggable and defaults to OpenStreetMap, so
  nothing in your own database is licensed out from under you or billed per lookup.

## Status

Early. Milestone 0 — project skeleton and a verified build.

| Milestone | Scope | State |
|---|---|---|
| M0 | Project skeleton, build, CI, APK on a device | in progress |
| M1 | Local-only: places, visits, dishes, opinions, search | planned |
| M2 | People, tags, photos, OSM place lookup | planned |
| M3 | Accounts, sync, shared lists | planned |
| M4 | Map, polish | planned |
| M5 | Play Store release | planned |

## Stack

Kotlin · Jetpack Compose · Material 3 · Navigation 3 · Room · Coroutines + Flow

Built with AGP 9 (Kotlin support is built into the Android Gradle plugin — there is
deliberately no `kotlin-android` plugin in the build files).

## Build

Requires JDK 17+ and an Android SDK. The Gradle wrapper handles the rest.

```sh
./gradlew assembleDebug          # build a debug APK
./gradlew test                   # unit tests
./gradlew connectedAndroidTest   # instrumented tests (needs a device)
./gradlew installDebug           # build and install to a connected device
```

Point the build at your SDK with a `local.properties` containing `sdk.dir=/path/to/Android/Sdk`,
or set `ANDROID_HOME`.

## License

Not yet chosen.
