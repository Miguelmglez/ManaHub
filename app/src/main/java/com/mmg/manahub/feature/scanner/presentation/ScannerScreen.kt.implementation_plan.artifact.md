# Implementation Plan - Scanner UI Refinement & Simplification

This plan simplifies the scanner by removing specialized modes (Quick Mode, Lookup Only) and redesigns the settings menu to expand vertically from the top-right gear icon.

## Proposed Changes

### [Component] Scanner Feature

#### [MODIFY] [ScannerUiState.kt](file:///E:/Projects/ManaHub/app/src/main/java/com/mmg/manahub/feature/scanner/presentation/ScannerUiState.kt)
- Remove `isQuickMode` and `isLookupOnly`.
- Remove `showSettingsSheet` as the modal is gone.

#### [MODIFY] [ScannerViewModel.kt](file:///E:/Projects/ManaHub/app/src/main/java/com/mmg/manahub/feature/scanner/presentation/ScannerViewModel.kt)
- Remove `onToggleQuickMode` and `onToggleLookupOnly`.
- Update `processRecognitionResult`:
    - Remove the mode-based branching.
    - Always call `quickAddCard(result.card)` once stability is met, unless the result is `ambiguous` (which still shows the ambiguity selector).
- Remove `onOpenSettings` and `onCloseSettings` (no longer needed for the modal).

#### [MODIFY] [ScannerScreen.kt](file:///E:/Projects/ManaHub/app/src/main/java/com/mmg/manahub/feature/scanner/presentation/ScannerScreen.kt)
- **TopScannerControls Redesign**:
    - Reorder icons: `[Back] .... [Pause] [Queue] [Language] [Settings]`.
    - Place the Settings button in a `Column` aligned to the top-end.
    - Change expansion direction: Use `AnimatedVisibility` with `slideInVertically(initialOffsetY = { -it })` to make Sound and Flash toggles drop down *from behind* the gear icon.
    - Remove Quick Mode and Lookup Only buttons from the expansion.
- **ScannerScreen Root**:
    - Update parameters passed to `TopScannerControls`.
    - Remove the `DetectedCardOverlay` mode-based UI logic (it will now simply show the "Last Added" card).

## Verification Plan

### Manual Verification
1.  **Vertical Expansion**: Tap the gear icon in the top right. Verify that the Sound and Flash icons slide down vertically below it.
2.  **Right Alignment**: Verify the gear icon is the rightmost element in the top bar.
3.  **Functionality Removal**: Verify there are no toggles for "Quick Mode" or "Lookup Only".
4.  **Auto-Add**: Scan a card. Verify it is automatically added to the queue (the previous "Quick Mode" behavior) without needing manual confirmation, unless it's ambiguous.
5.  **Visual States**: Verify that toggling Sound/Flash still provides gold background feedback when active.
