# Thumbnail Carousel Polish Plan

## Objective
Refine the usability and aesthetics of the Thumbnail Carousel by highlighting the active page, centering it *only* when the carousel is initially opened, preventing premature dismissal when selecting pages, ensuring proper state reset on menu close, and balancing visual spacing.

## Scope & Impact
- **ThumbnailCarousel.kt**:
  - Highlights the current page by passing `isCurrentPage = (index == currentPageIndex)`.
  - Animates scroll to center the current page *only when the carousel is opened*. When the user selects a new page, it will be highlighted but the carousel will not auto-scroll to center it.
  - Increases total container height to 180dp (making thumbnails ~10% larger) and adds balanced top/bottom padding to visually align the page numbers.
- **ProgressSlider.kt (BookPageThumbnail)**:
  - Adds an `isCurrentPage` parameter.
  - Dynamically changes the border color to the primary theme accent color when `isCurrentPage == true` (with 2dp thickness matching other thumbnails).
- **BottomSheetSettingsOverlay.kt & SettingsContent.kt**:
  - Removes the `onToggleCarousel()` calls triggered inside `onPageChange`, keeping the carousel open after navigating to a new page.
- **ReaderContent.kt**:
  - Adds a `LaunchedEffect(showSettingsMenu)` that resets `commonReaderState.showCarousel.value = false` whenever the user closes the overlay (e.g., by tapping the image).

## Implementation Steps
1. **Highlight & Border**: Modify `BookPageThumbnail` to accept `isCurrentPage: Boolean = false`. Use `MaterialTheme.colorScheme.primary` for the border color and 4dp width if true.
2. **Carousel Sizing & Initial Centering**: Update `ThumbnailCarousel.kt`:
   - Change `modifier.height(150.dp)` to `modifier.height(180.dp)`.
   - Update `contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)` and `verticalArrangement = Arrangement.spacedBy(8.dp)`.
   - Remove `LaunchedEffect(currentPageIndex)`. Instead, use `LaunchedEffect(lazyListState.layoutInfo.viewportSize.width)` paired with a boolean state (`initialScrollDone`) to center the list *exactly once* when the viewport is initialized.
3. **Persist on Selection**: In `BottomSheetSettingsOverlay.kt` and `SettingsContent.kt`, find `onPageChange = { ... }` block inside `ThumbnailCarousel` and remove the line that toggles the carousel closed.
4. **State Reset on Dismiss**: In `ReaderContent.kt`, add a `LaunchedEffect(showSettingsMenu)` that sets `commonReaderState.showCarousel.value = false` when `showSettingsMenu` becomes false.

## Verification & Testing
- Open the carousel: Verify it is centered on the current page, the active page is highlighted in the accent color, and the thumbnails are slightly larger with balanced top padding.
- Tap another page: Verify the reader jumps to that page, the carousel *remains open*, the tapped page is highlighted, and the carousel does *not* jump to center it.
- Close the UI by tapping the center of the screen: Reopen the UI and verify the standard slider appears instead of remembering the carousel was open.
