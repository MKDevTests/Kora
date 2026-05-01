# Reader Navigation Back Button Plan

## Objective
Implement an in-memory, session-scoped navigation history and a floating "Back" button for the readers. This feature allows users to return to their previous location after performing a jump via the slider, thumbnail carousel, or notes. The history mechanism and the Back button UI component will be built as **fully generic, common components** located in the shared `snd.komelia.ui.reader.common` package, ensuring seamless reusability across both the image/comic reader (implemented in this plan) and the EPUB3 reader (future integration). The Back button will use a history icon, appear with a fade animation, and maintain visual consistency with the reader's control panel.

## Key Files & Context

1.  **Shared State Holders**:
    *   `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/common/NavigationHistory.kt` (New file): To hold the generic `NavigationHistory` state class, `NavigationEntry`, `NavigationSource` enum, and the generic `NavigationLocation` interface.
2.  **Shared UI Component**:
    *   `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/common/ReaderNavigationBackButton.kt` (New file): A reusable, generic composable for the floating Back button that uses the `history` icon (`Icons.Default.History` or `Icons.Outlined.History`) and a fade-in/fade-out animation. This component is completely decoupled from any specific reader implementation so it can be dropped into the EPUB reader later.
3.  **Image Reader State Integration**:
    *   `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/ReaderState.kt`: To instantiate and hold the `NavigationHistory` for the image reader session.
4.  **Image Reader Jump Navigation Interception**:
    *   `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/settings/BottomSheetSettingsOverlay.kt` / `SettingsContent.kt`: To record jumps before invoking `onPageChange` from the Slider or Carousel.
    *   `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/common/ReaderContent.kt`: To record jumps when a note is clicked.
5.  **Image Reader Sequential Navigation (Swipe) Handling**:
    *   `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/paged/PagedReaderContent.kt`, `ContinuousReaderContent.kt`, `PanelsReaderContent.kt` (or their respective state classes): To detect sequential page swipes and dismiss the Back button.
6.  **Image Reader UI Placement**:
    *   `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/common/ReaderContent.kt`: To host the generic `ReaderNavigationBackButton` composable.

## Implementation Steps

1.  **Create the generic, common `NavigationHistory` mechanism**:
    *   Create file: `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/common/NavigationHistory.kt`.
    *   Define `NavigationSource` enum (`SLIDER`, `CAROUSEL`, `NOTES`, `BACK_BUTTON`).
    *   Define the generic `interface NavigationLocation`.
    *   Define reader-specific implementation: `data class ImagePageLocation(val page: Int) : NavigationLocation`. (EPUB will later define its own implementation of this interface).
    *   Define `NavigationEntry(val source: NavigationSource, val location: NavigationLocation)`.
    *   Implement `NavigationHistory` class with a `StateFlow<List<NavigationEntry>>` for history and a `StateFlow<Boolean>` for the button's visibility.
    *   Include methods: `addEntry(source, location)`, `popEntry(): NavigationEntry?`, `dismissBackButton()`, and `clear()`.

2.  **Build the generic, common Back Button UI**:
    *   Create file: `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/common/ReaderNavigationBackButton.kt`.
    *   Create a `ReaderNavigationBackButton` composable taking `isVisible` and `onClick` parameters.
    *   Use `AnimatedVisibility(visible = isVisible, enter = fadeIn(), exit = fadeOut())`.
    *   Use a round `Surface` or `Box` with `CircleShape` and apply the `HazeMaterials.thin()` background (or a matching surface color) to mimic the control panels.
    *   Use `Icon(Icons.Default.History, contentDescription = "Go back")`.

3.  **Integrate history into the Image Reader State**:
    *   Add `val navigationHistory = NavigationHistory()` to `snd.komelia.ui.reader.image.ReaderState`.

4.  **Track Jumps in the Image Reader**:
    *   In the UI layers that trigger jumps (Slider, Carousel, Notes in `ReaderContent.kt` and `BottomSheetSettingsOverlay.kt`/`SettingsContent.kt`), call `navigationHistory.addEntry(source, ImagePageLocation(currentPageIndex))` *before* invoking the reader's `onPageChange` or `scrollToBookPage`.
    *   This action will automatically set the Back button visibility to `true`.

5.  **Handle Swipes / Sequential Navigation in the Image Reader**:
    *   In the Paged, Continuous, and Panels reader components where sequential page scrolling is detected (e.g., `pagerState.isScrollInProgress` or inside the `onPageChange` implementations when not triggered by a jump), call `navigationHistory.dismissBackButton()`. This keeps the history intact but hides the button until the next jump.

6.  **Position the Back Button in the Image Reader**:
    *   Place the `ReaderNavigationBackButton` inside the main `Box` in `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/common/ReaderContent.kt`.
    *   Align it to `Alignment.BottomEnd`. Add vertical padding that increases when `showSettingsMenu` (the control panel) is true, ensuring it always floats above the active UI panels.

7.  **Wire up the Back Button Action**:
    *   When the Back button is clicked, call `navigationHistory.popEntry()`.
    *   If the entry is an `ImagePageLocation`, jump to that page.
    *   Record the *current* page as a new `BACK_BUTTON` jump (`navigationHistory.addEntry(NavigationSource.BACK_BUTTON, ImagePageLocation(currentPageIndex))`). As per requirements, the `BACK_BUTTON` source will keep the button hidden for now.

## Verification & Testing
*   **Slider & Carousel**: Navigate to page 10, use the slider/carousel to jump to page 50. Verify the floating Back button (history icon) appears with a fade.
*   **Back Navigation**: Press the Back button. Verify the view jumps back to page 10. Verify the button fades out and is no longer visible.
*   **Swipe Dismissal**: Jump from page 10 to 50. Verify the button appears. Swipe sequentially to page 51. Verify the button fades out.
*   **Sequential History Integrity**: After the button is dismissed by swiping, verify that a new jump correctly adds to the history and re-shows the button.
*   **UI Overlay Alignment**: Verify the button sits at the bottom right when the control panel is off, and dynamically moves up to float above the control panel/carousel when they are turned on.
*   **Architecture Check**: Ensure `NavigationHistory.kt` and `ReaderNavigationBackButton.kt` contain no image-reader-specific logic (except the defined `ImagePageLocation` data class, which implements the generic interface).