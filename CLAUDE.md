# Forklore — working conventions

Read this before changing code. It exists so that work done in parallel worktrees comes
out looking like it was written by one person.

## What this app is

A personal, offline-first Android app for tracking restaurants and — more importantly —
individual dishes at those restaurants. Two people share a list; both record their own
opinions of the same dish; both maintain want-to-try lists. See README.md for the source
notes that motivated the data model.

## Non-negotiables

1. **The app works offline, with no account.** Never introduce a code path that requires
   network or sign-in to view, create, or edit local data. Sync and sharing are additive.
2. **Room is the source of truth for the UI.** ViewModels observe Room via `Flow`. They do
   not call the network directly. Remote data is written into Room and the UI reacts.
3. **External place data is a cache, never authoritative.** A `Place` row keeps our own
   name/address; provider fields (`providerId`) are for refresh and dedupe only. The app
   must render correctly when every provider field is null.
4. **Free text is first-class.** Every entity that a person writes about has a plain
   `note: String` field. Structured fields supplement it; they never replace it and never
   become required in the UI.
5. **Opinions are per-author rows, not columns.** Two people rating the same dish produce
   two `DishOpinion` rows. Never collapse them into one field — it loses the disagreement,
   which is data the users want.

## Architecture

Single `:app` module, package-by-feature. Split into modules only when it demonstrably
hurts, not preemptively.

```
com.jasonarends.forklore
├── data
│   ├── db          Room entities, DAOs, database, migrations, converters
│   ├── repository  Repository interfaces + implementations
│   └── place       PlaceProvider abstraction (manual, OSM, optional Google)
├── di              Manual DI container (see below)
├── ui
│   ├── <feature>   One package per screen: Screen.kt, ViewModel.kt, UiState
│   └── theme       Colors, type, theme
└── Navigation.kt   Navigation 3 back stack + entries
```

Unidirectional data flow: `Room → Repository → ViewModel (StateFlow<UiState>) → Composable`.
Events flow back as function references passed into composables. Follow the shape already
in `ui/main/MainScreenViewModel.kt` — a sealed `UiState` with `Loading`/`Error`/`Success`
and `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Loading)`.

## Dependency injection

**No Hilt.** Dependencies are constructed by hand in a single `AppContainer` held by the
`Application`. ViewModels take their dependencies as constructor parameters and are created
through a `ViewModelProvider.Factory`. This is deliberate: the object graph is small, and a
hand-written container keeps builds fast and the wiring readable. Do not add Hilt, Koin, or
Dagger without raising it first.

## Dependencies

Adding a dependency requires a reason that can't be met by the stdlib, Kotlin coroutines,
or something already in `gradle/libs.versions.toml`. All versions go in the version catalog
— never inline a version string in a `build.gradle.kts`.

This project builds with **AGP 9**, which has built-in Kotlin support. There is no
`org.jetbrains.kotlin.android` plugin and there must not be one. Kotlin compiler options go
in the top-level `kotlin { }` block, not `android.kotlinOptions` (which no longer exists).
Annotation processing uses KSP, never kapt.

## Testing

- Every repository and every ViewModel gets unit tests. Use fakes, not mocking frameworks.
- Every Room schema change ships with a migration **and** a migration test. Schemas are
  exported to `app/schemas/` and committed — never delete them.
- Compose UI tests cover the primary path of each screen, not every state.
- `./gradlew test` must pass before any commit. Don't commit red.

## Style

- Kotlin official style, 2-space indent (matches the existing files).
- Name things the way the domain does: `Place`, `PlaceEntry`, `Visit`, `Dish`,
  `DishOpinion`, `DishInterest`, `Person`, `Collection`. Don't invent synonyms —
  no `Restaurant`, `Review`, or `Item`.
- Composables are stateless where practical: state in, events out. A composable that
  reaches for a ViewModel should be the screen-level one only.
- No comments that restate the code. Comment *why*, and only when it isn't obvious.

## Commands

```sh
./gradlew assembleDebug          # debug APK
./gradlew test                   # unit tests
./gradlew connectedAndroidTest   # instrumented tests (device required)
./gradlew installDebug           # install to connected device
./gradlew lint                   # Android lint
```

## Domain glossary

| Term | Meaning |
|---|---|
| `Collection` | A list of places, shared with zero or more people. A personal list is a collection with one member. Everything belongs to one. |
| `Place` | A restaurant. Identity is ours; provider IDs are hints for refresh/dedupe. |
| `PlaceEntry` | A place's membership in a collection, carrying status (want / visited / avoid). |
| `Visit` | One occasion at a place. Date is nullable and carries a precision (day / month / year). |
| `Dish` | A named menu item at a place, with aliases — users spell the same dish three ways. |
| `DishInterest` | A collection's stance on a dish: want to try, tried, never again. May be scoped to a `Person`. |
| `DishOpinion` | One author's rating and note for a dish. Multiple per dish by design. |
| `Person` | Someone whose preferences or recommendations are tracked. May or may not be an app user. |
