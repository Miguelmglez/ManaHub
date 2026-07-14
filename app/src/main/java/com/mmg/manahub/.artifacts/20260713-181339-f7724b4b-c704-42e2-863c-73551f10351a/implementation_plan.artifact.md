# Improve Error Handling and UI in AddCardScreen

Modify the error handling in `AddCardScreen` to specifically handle Scryfall 404 (Not Found) errors by showing a user-friendly empty state instead of a technical error message or toast. Additionally, increase the height of the camera FAB to match the `CollectionScreen` layout.

## Proposed Changes

### Core Data / Repository

Detect Scryfall 404 errors and return a specific error identifier.

#### [CardRepositoryImpl.kt](file:///E:/Projects/ManaHub/app/src/main/java/com/mmg/manahub/core/data/repository/CardRepositoryImpl.kt)

- In `searchCardsPaginated`, check if the exception is a `ClientRequestException` with status 404.
- Return `DataResult.Error("SCRYFALL_404")` when a 404 is detected.

### Add Card Feature

Handle the specific 404 error and provide a way to clear all search parameters.

#### [AddCardViewModel.kt](file:///E:/Projects/ManaHub/app/src/main/java/com/mmg/manahub/feature/addcard/presentation/AddCardViewModel.kt)

- Add `onClearAll()` method that resets the query text, advanced filters, search results, and error state.
- Ensure `textQueryFlow` and `activeQueryFlow` are updated to trigger a fresh (idle) state.

#### [AddCardScreen.kt](file:///E:/Projects/ManaHub/feature/addcard/presentation/AddCardScreen.kt)

- **Error Handling**:
    - In `SearchSurface`, check if `uiState.error == "SCRYFALL_404"`.
    - If true, display an `EmptyState` with:
        - Title: `R.string.addcard_no_results` ("No cards found")
        - Subtitle: `R.string.addcard_no_results_subtitle` ("Try searching in another language...")
        - Action: `viewModel.onClearAll()` with label `R.string.collection_clear_filters` ("Clear filters").
- **FAB Height**:
    - Wrap the `FloatingActionButton` in a `Box` with `padding(bottom = 80.dp)` to elevate it, matching the height it has in `CollectionScreen` (where it's pushed up by the `MagicBottomBar`).

## Verification Plan

### Automated Tests
- Run existing ViewModel tests: `./gradlew :app:testDebugUnitTest --tests "com.mmg.manahub.feature.addcard.presentation.AddCardViewModelTest"`

### Manual Verification
1. **Search with no results**: Enter a gibberish string in the search field that you know won't match any cards (e.g., "asdfghjkl123").
2. **Verify Error State**:
    - Ensure NO `MagicToast` appears.
    - Ensure the central area shows the "No cards found" empty state.
    - Verify the "Clear filters" button is present.
3. **Verify Clear All**:
    - Click the "Clear filters" button.
    - Verify the search text is cleared and the screen returns to the "Discovery" (spotlight) grid.
4. **Verify FAB Height**:
    - Navigate between `CollectionScreen` and `AddCardScreen`.
    - Verify the camera FAB in `AddCardScreen` is at the same vertical position as the "+" FAB in `CollectionScreen`.
