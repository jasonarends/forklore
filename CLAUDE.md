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
   The database is included in Android cloud backup (see `res/xml/data_extraction_rules.xml`),
   which means M3 sync must treat a restored database as a peer to merge, never as
   authoritative and never as empty.
2. **Room is the source of truth for saved data.** ViewModels observe Room via `Flow`; saved
   data reaches the UI no other way. The one carve-out: *transient* provider results — an OSM
   typeahead list the user is still scrolling — are ViewModel-local state and are **not**
   written to Room. Room is written when the user picks something. Never persist search
   results speculatively; it pollutes the database with places nobody chose and puts rule 3's
   dedupe at war with itself.
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
Events flow back as function references passed into composables.

Every ViewModel follows this shape (the template's `MainScreenViewModel` is a placeholder
that M1 deletes — this doc, not that file, is the reference):

```kotlin
class PlaceListViewModel(placeRepository: PlaceRepository) : ViewModel() {
  val uiState: StateFlow<PlaceListUiState> =
    placeRepository.places
      .map<List<Place>, PlaceListUiState>(PlaceListUiState::Success)
      .catch { emit(PlaceListUiState.Error(it)) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlaceListUiState.Loading)

  companion object {
    val Factory = viewModelFactory {
      initializer {
        val app = this[APPLICATION_KEY] as ForkloreApp
        PlaceListViewModel(app.container.placeRepository)
      }
    }
  }
}

sealed interface PlaceListUiState {
  data object Loading : PlaceListUiState
  data class Error(val throwable: Throwable) : PlaceListUiState
  data class Success(val places: List<Place>) : PlaceListUiState
}
```

## Dependency injection

**No Hilt.** Dependencies are constructed by hand in a single `AppContainer` held by
`ForkloreApp : Application`. This is deliberate: the object graph is a handful of DAOs and
repositories, and a hand-written container keeps builds fast and the wiring readable. Do not
add Hilt, Koin, or Dagger without raising it first.

There is exactly **one** way a composable reaches the container, and it is the `Factory`
companion shown above, used as `viewModel(factory = PlaceListViewModel.Factory)` in the
screen-level composable. Do not cast the `Application` inside a composable, do not introduce
a `CompositionLocal` for the container, and do not thread the container through composable
parameters. Only screen-level composables touch a ViewModel at all; everything below them
takes state in and emits events out.

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

- Formatting is not a matter of opinion here: `spotless` with `ktfmt` Google style (2-space)
  owns it. Run `./gradlew spotlessApply` before committing; CI runs `spotlessCheck`.
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
| `PlaceList` | A list of places, shared with zero or more people. A personal list is a `PlaceList` with one member. Everything belongs to one. Named `PlaceList`, not `Collection`, so it doesn't shadow `kotlin.collections.Collection`. |
| `Place` | A restaurant. Identity is ours; provider IDs are hints for refresh/dedupe. |
| `PlaceEntry` | A place's membership in a `PlaceList`, carrying status (want / visited / avoid). |
| `Visit` | One occasion at a place. Date is nullable and carries a precision (day / month / year). |
| `Dish` | A named menu item at a place, with aliases — users spell the same dish three ways. |
| `DishInterest` | A `PlaceList`'s stance on a dish: want to try, tried, never again. May be scoped to a `Person`. |
| `DishOpinion` | One author's rating and note for a dish. Multiple per dish by design. |
| `Person` | Someone whose preferences or recommendations are tracked. May or may not be an app user. |
