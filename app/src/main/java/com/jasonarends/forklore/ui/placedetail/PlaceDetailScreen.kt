package com.jasonarends.forklore.ui.placedetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jasonarends.forklore.data.db.DishAliasEntity
import com.jasonarends.forklore.data.db.DishEntity
import com.jasonarends.forklore.data.db.DishWithAliases
import com.jasonarends.forklore.data.db.PlaceEntity
import com.jasonarends.forklore.data.db.PlaceEntryEntity
import com.jasonarends.forklore.data.db.PlaceEntryWithPlace
import com.jasonarends.forklore.data.db.PlaceStatus
import com.jasonarends.forklore.data.db.Rating
import com.jasonarends.forklore.data.db.RevisitIntent
import com.jasonarends.forklore.ui.components.EmptyState
import com.jasonarends.forklore.ui.components.LedgerChip
import com.jasonarends.forklore.ui.components.LedgerGlyph
import com.jasonarends.forklore.ui.components.LedgerIcon
import com.jasonarends.forklore.ui.components.LedgerPrimaryButton
import com.jasonarends.forklore.ui.components.LedgerTextField
import com.jasonarends.forklore.ui.components.LedgerTopBar
import com.jasonarends.forklore.ui.components.NoteField
import com.jasonarends.forklore.ui.components.PlaceStatusPicker
import com.jasonarends.forklore.ui.components.RatingPicker
import com.jasonarends.forklore.ui.components.RevisitIntentPicker
import com.jasonarends.forklore.ui.components.SectionHeader
import com.jasonarends.forklore.ui.theme.ForkloreTheme
import com.jasonarends.forklore.ui.theme.ForkloreType
import com.jasonarends.forklore.ui.theme.dashedBorder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceDetailScreen(
  placeEntryId: String,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: PlaceDetailViewModel = viewModel(factory = PlaceDetailViewModel.factory(placeEntryId)),
  dishesViewModel: DishesViewModel = viewModel(factory = DishesViewModel.factory(placeEntryId)),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  // One local snapshot, read once: `state` is a delegated property backed by `State<T>.value`,
  // which
  // isn't smart-castable (each read can return a different instance across recompositions), so both
  // the title below and the `when` in Scaffold's content match on this same captured `current`
  // instead of re-reading `state` a second time and risking the two disagreeing.
  val current = state
  // The top bar repeats the same name/branch the body heading shows (see PlaceDetail below), so
  // it needs the name before the rest of the screen is ready to render.
  val title =
    (current as? PlaceDetailUiState.Success)?.entry?.let {
      listOfNotNull(it.place.name, it.place.branchLabel).joinToString(" · ")
    } ?: "Place"
  Scaffold(
    modifier = modifier,
    topBar = { LedgerTopBar(title = title, subtitle = "the receipts", onBack = onBack) },
    containerColor = ForkloreTheme.colors.paper,
  ) { innerPadding ->
    when (current) {
      PlaceDetailUiState.Loading -> Unit
      PlaceDetailUiState.NotFound ->
        EmptyState("This place couldn't be found.", Modifier.padding(innerPadding))
      is PlaceDetailUiState.Error ->
        Text(
          "Couldn't load this place: ${current.throwable.message}",
          Modifier.padding(innerPadding),
        )
      is PlaceDetailUiState.Success -> {
        val dishesState by dishesViewModel.uiState.collectAsStateWithLifecycle()
        val dishQuery by dishesViewModel.query.collectAsStateWithLifecycle()
        val dishSuggestions by dishesViewModel.suggestions.collectAsStateWithLifecycle()
        PlaceDetail(
          entry = current.entry,
          onStatusChange = viewModel::updateStatus,
          onFoodRatingChange = viewModel::updateFoodRating,
          onServiceRatingChange = viewModel::updateServiceRating,
          onRevisitIntentChange = viewModel::updateRevisitIntent,
          onNoteChange = viewModel::updateNote,
          dishesSection = {
            DishesSection(
              state = dishesState,
              query = dishQuery,
              suggestions = dishSuggestions,
              onQueryChange = dishesViewModel::onQueryChange,
              onAddDish = dishesViewModel::addDish,
              onAddAlias = dishesViewModel::addAlias,
            )
          },
          modifier = Modifier.padding(innerPadding),
        )
      }
    }
  }
}

/**
 * Stateless by design: state in, events out. Only the screen-level composable sees a ViewModel.
 *
 * The large heading repeats the name/branch already shown in the top bar (see [PlaceDetailScreen])
 * — a deliberate "ledger repeats its own header" touch straight from the frozen mockup, not an
 * oversight. It only reads as duplication when both render in the same semantics tree, which
 * happens in the full [PlaceDetailScreen] (never asserted on by exact place name in this codebase's
 * tests) but not when this composable is exercised directly, as most of this file's tests do.
 */
@Composable
internal fun PlaceDetail(
  entry: PlaceEntryWithPlace,
  onStatusChange: (PlaceStatus) -> Unit,
  onFoodRatingChange: (Rating?) -> Unit,
  onServiceRatingChange: (Rating?) -> Unit,
  onRevisitIntentChange: (RevisitIntent?) -> Unit,
  onNoteChange: (String) -> Unit,
  dishesSection: @Composable () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = ForkloreTheme.colors
  Column(
    modifier =
      modifier
        .fillMaxSize()
        .background(colors.paper)
        .padding(horizontal = 20.dp)
        .verticalScroll(rememberScrollState())
  ) {
    Text(
      text = listOfNotNull(entry.place.name, entry.place.branchLabel).joinToString(" · "),
      style = ForkloreType.placeNameDetail,
      color = colors.ink,
      modifier = Modifier.padding(top = 14.dp),
    )
    entry.place.address?.let {
      Text(
        text = it,
        style = ForkloreType.branchLabel,
        color = colors.ink2,
        modifier = Modifier.padding(top = 4.dp),
      )
    }
    entry.place.warning?.let { warning -> WarningCard(warning) }

    SectionHeader("Status")
    PlaceStatusPicker(status = entry.entry.status, onStatusChange = onStatusChange)

    // Food and service get their own headers, not a shared "Rating" section: divine pasta and
    // rude servers are two different verdicts and must never read as one. testTag lets tests tell
    // the two pickers apart — their rating labels are otherwise identical text.
    SectionHeader("Food")
    RatingPicker(
      rating = entry.entry.foodRating,
      onRatingChange = onFoodRatingChange,
      modifier = Modifier.testTag("food"),
    )

    SectionHeader("Service")
    RatingPicker(
      rating = entry.entry.serviceRating,
      onRatingChange = onServiceRatingChange,
      modifier = Modifier.testTag("service"),
    )

    SectionHeader("Would we go back?")
    RevisitIntentPicker(intent = entry.entry.revisitIntent, onIntentChange = onRevisitIntentChange)

    SectionHeader("Dishes")
    dishesSection()

    // No SectionHeader here: NoteField already carries its own "Note" label, and a second one
    // above it would just be the same word twice. testTag disambiguates it from the dish fields
    // above, which are also text inputs on this same screen.
    NoteField(
      value = entry.entry.note,
      onValueChange = onNoteChange,
      modifier = Modifier.padding(top = 16.dp, bottom = 20.dp).testTag("place-note"),
    )
  }
}

/**
 * Every dish recorded at this place entry, plus the field that adds one. `internal` (not `private`)
 * so tests can exercise it directly rather than through the whole [PlaceDetail] column. [DishRow]'s
 * name row leaves a trailing slot for issue #7's per-dish status chip, and space below it for
 * issue #8's opinion cards — neither is wired in yet.
 */
@Composable
internal fun DishesSection(
  state: DishesUiState,
  query: String,
  suggestions: List<DishWithAliases>,
  onQueryChange: (String) -> Unit,
  onAddDish: (String) -> Unit,
  onAddAlias: (dishId: String, alias: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = ForkloreTheme.colors
  Column(modifier = modifier.fillMaxWidth()) {
    when (state) {
      DishesUiState.Loading -> Unit
      is DishesUiState.Error ->
        Text(
          "Couldn't load dishes: ${state.throwable.message}",
          style = ForkloreType.fieldInput,
          color = colors.stamp,
        )
      is DishesUiState.Success -> {
        if (state.dishes.isEmpty()) {
          EmptyState("No dishes yet.")
        } else {
          state.dishes.forEach { dish ->
            key(dish.dish.id) {
              DishRow(
                dish = dish,
                onAddAlias = { alias -> onAddAlias(dish.dish.id, alias) },
                modifier = Modifier.testTag("dish-row-${dish.dish.id}"),
              )
            }
          }
        }
        // Only rendered in the Success branch: on Error the write would still land (it hits the
        // database directly, not this composable's state), but the list never recovers to show
        // it, so the user would submit into a field and see nothing happen. Loading has no
        // suggestions to offer yet either way.
        DishEntryField(
          query = query,
          suggestions = suggestions,
          onQueryChange = onQueryChange,
          onSubmit = onAddDish,
          modifier = Modifier.padding(top = 8.dp),
        )
      }
    }
  }
}

/** One recorded dish, its aliases, and the affordance to add another spelling. */
@Composable
private fun DishRow(
  dish: DishWithAliases,
  onAddAlias: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = ForkloreTheme.colors
  Column(modifier = modifier.fillMaxWidth().padding(vertical = 10.dp)) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(text = dish.dish.canonicalName, style = ForkloreType.dishName, color = colors.ink)
    }
    if (dish.aliases.isNotEmpty()) {
      Text(
        text = "also: " + dish.aliases.joinToString(", ") { it.alias },
        style = ForkloreType.branchLabel,
        color = colors.ink2,
        modifier = Modifier.padding(top = 2.dp),
      )
    }
    AliasEntry(onAdd = onAddAlias, modifier = Modifier.padding(top = 6.dp))
  }
}

/** A collapsed "+ alternate spelling" link that expands into a field, mirroring [PersonPicker]. */
@Composable
private fun AliasEntry(onAdd: (String) -> Unit, modifier: Modifier = Modifier) {
  var expanded by rememberSaveable { mutableStateOf(false) }
  var text by rememberSaveable { mutableStateOf("") }
  val colors = ForkloreTheme.colors
  if (expanded) {
    Row(
      modifier = modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      LedgerTextField(
        value = text,
        onValueChange = { text = it },
        label = "Alternate spelling",
        modifier = Modifier.weight(1f).testTag("alias-field"),
      )
      TextButton(
        enabled = text.isNotBlank(),
        onClick = {
          onAdd(text.trim())
          text = ""
          expanded = false
        },
      ) {
        Text("Add")
      }
    }
  } else {
    TextButton(onClick = { expanded = true }, modifier = modifier) {
      Text("+ Alternate spelling", color = colors.ink2)
    }
  }
}

/**
 * The "add a dish" field: typing offers matching [suggestions] as chips (see
 * [DishesViewModel.suggestions]) so a dish already recorded under a different spelling is picked
 * rather than re-typed into a duplicate; tapping one submits it exactly as [onSubmit] would. The
 * button submits whatever was typed regardless — `DishRepository.findOrCreateDish` is what actually
 * guards against a duplicate landing in Room; this field only makes the existing option visible.
 */
@Composable
private fun DishEntryField(
  query: String,
  suggestions: List<DishWithAliases>,
  onQueryChange: (String) -> Unit,
  onSubmit: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      LedgerTextField(
        value = query,
        onValueChange = onQueryChange,
        label = "Add a dish",
        capitalization = KeyboardCapitalization.Words,
        modifier = Modifier.weight(1f).testTag("dish-query-field"),
      )
      LedgerPrimaryButton(
        text = "Add",
        onClick = { onSubmit(query) },
        enabled = query.isNotBlank(),
      )
    }
    if (suggestions.isNotEmpty()) {
      FlowRow(
        modifier = Modifier.padding(top = 6.dp).testTag("dish-suggestions"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        suggestions.forEach { suggestion ->
          LedgerChip(
            label = suggestion.dish.canonicalName,
            selected = false,
            onClick = { onSubmit(suggestion.dish.canonicalName) },
            modifier = Modifier.testTag("dish-suggestion-${suggestion.dish.id}"),
          )
        }
      }
    }
  }
}

/**
 * The dashed-stamp warning card, per issue #15: 2px dashed `stamp` border with a circle-slash icon.
 * The mockup bolds the opening clause via a one-off fixture; a real generic bold-first- clause
 * parser isn't warranted for arbitrary free text, so this renders the warning plainly.
 */
@Composable
private fun WarningCard(warning: String) {
  val colors = ForkloreTheme.colors
  Surface(
    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).dashedBorder(colors.stamp),
    shape = RoundedCornerShape(3.dp),
    color = colors.card,
    contentColor = colors.stamp,
  ) {
    Row(modifier = Modifier.padding(11.dp)) {
      LedgerIcon(
        LedgerGlyph.CircleSlash,
        tint = colors.stamp,
        modifier = Modifier.padding(end = 8.dp),
      )
      Text(text = warning, style = ForkloreType.fieldInput, color = colors.stamp)
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun PlaceDetailPopulatedPreview() {
  val place =
    PlaceEntity(
      name = "Hotel Brannock",
      address = "123 Main St",
      warning = "\$27 per person even if you order one thing",
      createdAt = 0,
      updatedAt = 0,
    )
  ForkloreTheme {
    Surface {
      PlaceDetail(
        entry =
          PlaceEntryWithPlace(
            entry =
              PlaceEntryEntity(
                placeListId = "list",
                placeId = place.id,
                status = PlaceStatus.VISITED,
                foodRating = Rating.LIFE_CHANGING,
                serviceRating = Rating.BAD,
                revisitIntent = RevisitIntent.WAIT,
                note = "Servers are rude, food was incredible.",
                createdAt = 0,
                updatedAt = 0,
              ),
            place = place,
          ),
        onStatusChange = {},
        onFoodRatingChange = {},
        onServiceRatingChange = {},
        onRevisitIntentChange = {},
        onNoteChange = {},
        dishesSection = {
          DishesSection(
            state =
              DishesUiState.Success(
                listOf(
                  DishWithAliases(
                    dish =
                      DishEntity(
                        placeEntryId = "entry",
                        canonicalName = "Barrel Potatoes",
                        normalizedName = "barrel potatoes",
                        createdAt = 0,
                        updatedAt = 0,
                      ),
                    aliases =
                      listOf(
                        DishAliasEntity(
                          dishId = "dish",
                          alias = "potatoe barrels",
                          normalized = "potatoe barrels",
                          createdAt = 0,
                          updatedAt = 0,
                        )
                      ),
                  )
                )
              ),
            query = "",
            suggestions = emptyList(),
            onQueryChange = {},
            onAddDish = {},
            onAddAlias = { _, _ -> },
          )
        },
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun PlaceDetailEmptyPreview() {
  val place = PlaceEntity(name = "Halberd", createdAt = 0, updatedAt = 0)
  ForkloreTheme {
    Surface {
      PlaceDetail(
        entry =
          PlaceEntryWithPlace(
            entry =
              PlaceEntryEntity(
                placeListId = "list",
                placeId = place.id,
                createdAt = 0,
                updatedAt = 0,
              ),
            place = place,
          ),
        onStatusChange = {},
        onFoodRatingChange = {},
        onServiceRatingChange = {},
        onRevisitIntentChange = {},
        onNoteChange = {},
        dishesSection = {
          DishesSection(
            state = DishesUiState.Success(emptyList()),
            query = "",
            suggestions = emptyList(),
            onQueryChange = {},
            onAddDish = {},
            onAddAlias = { _, _ -> },
          )
        },
      )
    }
  }
}
