package snd.komelia.ui.common.immersive

import snd.komelia.ui.common.ThumbnailConstants.ASPECT_RATIO
import snd.komelia.ui.common.ThumbnailConstants.CARD_SCALE
import snd.komelia.ui.LocalCardHeightScale
import snd.komelia.ui.LocalCardWidthScale
import snd.komelia.ui.LocalCardShadowLevel
import snd.komelia.ui.LocalCardCornerRadius
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.VectorizedAnimationSpec
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import snd.komelia.ui.LocalAnimatedVisibilityScope
import snd.komelia.ui.LocalImmersiveColorAlpha
import snd.komelia.ui.LocalImmersiveColorEnabled
import snd.komelia.ui.LocalRawNavBarHeight
import snd.komelia.ui.LocalRawStatusBarHeight
import snd.komelia.ui.LocalSharedTransitionScope
import snd.komelia.ui.LocalTransparentNavBarPadding
import snd.komelia.ui.LocalUseFloatingNavigationBar
import snd.komelia.ui.LocalUseImmersiveMorphingCover
import snd.komelia.ui.common.images.ThumbnailImage
import kotlin.math.roundToInt

private enum class CardDragValue { COLLAPSED, EXPANDED }

private class DirectionalSnapSpec : AnimationSpec<Float> {
    override fun <V : AnimationVector> vectorize(
        converter: TwoWayConverter<Float, V>
    ): VectorizedAnimationSpec<V> {
        val expandSpec = tween<Float>(
            durationMillis = 500,
            easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
        ).vectorize(converter)
        val collapseSpec = tween<Float>(
            durationMillis = 200,
            easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
        ).vectorize(converter)
        return object : VectorizedAnimationSpec<V> {
            override val isInfinite = false
            private fun pick(initialValue: V, targetValue: V) =
                if (converter.convertFromVector(targetValue) < converter.convertFromVector(initialValue)) expandSpec else collapseSpec
            override fun getDurationNanos(initialValue: V, initialVelocity: V, targetValue: V) =
                pick(initialValue, targetValue).getDurationNanos(initialValue, initialVelocity, targetValue)
            override fun getValueFromNanos(playTimeNanos: Long, initialValue: V, targetValue: V, initialVelocity: V) =
                pick(initialValue, targetValue).getValueFromNanos(playTimeNanos, initialValue, targetValue, initialVelocity)
            override fun getVelocityFromNanos(playTimeNanos: Long, initialValue: V, targetValue: V, initialVelocity: V) =
                pick(initialValue, targetValue).getVelocityFromNanos(playTimeNanos, initialValue, targetValue, initialVelocity)
        }
    }
}

private val emphasizedEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
private val emphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ImmersiveDetailScaffold(
    coverData: Any,
    coverKey: String,
    cardColor: Color?,
    modifier: Modifier = Modifier,
    immersive: Boolean = false,
    initiallyExpanded: Boolean = false,
    onExpandChange: (Boolean) -> Unit = {},
    topBarContent: @Composable () -> Unit,
    fabContent: @Composable () -> Unit,
    heroTextContent: (@Composable (expandFraction: Float) -> Unit)? = null,
    /**
     * Anchored to the SCREEN, not to the card — which is the whole point of it
     * being a slot here rather than something the caller puts in [cardContent].
     * The card is a full screen tall and is offset DOWN by up to 65% of the
     * screen while collapsed, so anything centered inside it sits below the
     * fold exactly when the cover is on show, and any touch target it carries
     * is unreachable over the cover.
     */
    overlayContent: @Composable BoxScope.() -> Unit = {},
    thumbnailWidth: Dp = 110.dp,
    cardContent: @Composable ColumnScope.(
        expandFraction: Float,
        onThumbnailPositioned: (LayoutCoordinates) -> Unit,
        onTextPositioned: (LayoutCoordinates) -> Unit,
    ) -> Unit,
) {
    val density = LocalDensity.current
    val immersiveColorEnabled = LocalImmersiveColorEnabled.current
    val immersiveColorAlpha = LocalImmersiveColorAlpha.current
    val surface = MaterialTheme.colorScheme.surface
    val backgroundColor = if (cardColor != null && immersiveColorEnabled) {
        cardColor.copy(alpha = immersiveColorAlpha).compositeOver(surface)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val scrimColor = if (immersiveColorEnabled) backgroundColor.copy(alpha = 0.72f) else backgroundColor
    val useMorphingCover = LocalUseImmersiveMorphingCover.current

    // Read shared transition scopes OUTSIDE BoxWithConstraints (which uses SubcomposeLayout).
    // SubcomposeLayout defers content composition to the layout phase, so any CompositionLocal
    // reads inside it happen too late for SharedTransitionLayout's composition-phase matching.
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current

    // Whether a shared transition is in progress — used to suppress crossfade during animation.
    val inSharedTransition = sharedTransitionScope != null && animatedVisibilityScope != null

    // Cover image is the shared element — flies from source thumbnail to its destination position.
    // Must be computed outside BoxWithConstraints for composition-phase matching.
    val coverSharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                rememberSharedContentState(key = "cover-$coverKey"),
                animatedVisibilityScope = animatedVisibilityScope,
                enter = fadeIn(tween(400, easing = emphasizedEasing)),
                exit = fadeOut(tween(300, easing = emphasizedAccelerateEasing)),
                boundsTransform = { _, _ -> tween(durationMillis = 600, easing = emphasizedEasing) },
            )
        }
    } else Modifier

    // Scaffold fades in behind the flying cover image and sliding card.
    // No slide here — the card handles its own slide via cardOverlayModifier. A slide on
    // BoxWithConstraints would move the layout positions of all children, causing the card to
    // double-animate and causing the cover sharedBounds destination to shift mid-transition.
    val scaffoldEnterExitModifier = if (animatedVisibilityScope != null) {
        with(animatedVisibilityScope) {
            Modifier.animateEnterExit(
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(200, easing = emphasizedAccelerateEasing))
            )
        }
    } else Modifier

    // Card enters in the SharedTransition overlay at z=0.5 — always above the cover image (z=0
    // from sharedBounds default). This prevents the z-flip where the source thumbnail image
    // would otherwise appear on top of the card during the container-transform crossfade.
    val cardOverlayModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            with(animatedVisibilityScope) {
                Modifier
                    .renderInSharedTransitionScopeOverlay(zIndexInOverlay = 0.5f)
                    .animateEnterExit(
                        enter = slideInVertically(tween(500, easing = emphasizedEasing)) { it / 4 }
                                + fadeIn(tween(300)),
                        exit = slideOutVertically(tween(400, easing = emphasizedAccelerateEasing)) { it / 4 }
                               + fadeOut(tween(300))
                    )
            }
        }
    } else Modifier

    val uiEnterExitModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            with(animatedVisibilityScope) {
                Modifier
                    .renderInSharedTransitionScopeOverlay(zIndexInOverlay = 0.75f)
                    .animateEnterExit(
                        enter = fadeIn(tween(durationMillis = 500)),
                        exit = fadeOut(tween(durationMillis = 100))
                    )
            }
        }
    } else Modifier

    val fabOverlayModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            with(animatedVisibilityScope) {
                Modifier
                    .renderInSharedTransitionScopeOverlay(zIndexInOverlay = 1f)
                    .animateEnterExit(
                        enter = fadeIn(tween(300, delayMillis = 50)),
                        exit = slideOutVertically(tween(200, easing = emphasizedAccelerateEasing)) { it / 2 }
                               + fadeOut(tween(150))
                    )
            }
        }
    } else Modifier

    BoxWithConstraints(modifier = modifier.fillMaxSize().then(scaffoldEnterExitModifier)) {
        val screenHeight = maxHeight
        val statusBarDp = LocalRawStatusBarHeight.current
        val navBarDp    = LocalRawNavBarHeight.current
        val windowHeightDp = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
        // Use actual window height (invariant to app nav bar showing/hiding) for stable collapsedOffset.
        val stableScreenHeight = windowHeightDp - statusBarDp - navBarDp
        val collapsedOffset = if (useMorphingCover) (stableScreenHeight * 0.65f) + statusBarDp else stableScreenHeight * 0.65f
        val collapsedOffsetPx = with(density) { collapsedOffset.toPx() }
        val screenWidth = maxWidth

        var targetThumbnailOffset by remember { mutableStateOf(Offset.Zero) }
        var targetTextOffset by remember { mutableStateOf(Offset.Zero) }
        var heroTextHeight by remember { mutableStateOf(0.dp) }
        var scaffoldCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

        // Use remember (not rememberSaveable) so pager pages don't restore stale saved state.
        var savedExpanded by remember { mutableStateOf(initiallyExpanded) }

        val state = remember(collapsedOffsetPx) {
            AnchoredDraggableState(
                initialValue = if (savedExpanded) CardDragValue.EXPANDED else CardDragValue.COLLAPSED,
                anchors = DraggableAnchors {
                    CardDragValue.COLLAPSED at collapsedOffsetPx
                    CardDragValue.EXPANDED at 0f
                },
                positionalThreshold = { d -> d * 0.5f },
                velocityThreshold = { with(density) { 100.dp.toPx() } },
                // M3 Emphasize Decelerate (expand, 500ms) / Emphasize Accelerate (collapse, 200ms)
                snapAnimationSpec = DirectionalSnapSpec(),
                decayAnimationSpec = exponentialDecay(),
            )
        }

        val cardOffsetPx = if (state.offset.isNaN()) collapsedOffsetPx else state.offset
        val expandFraction = (1f - cardOffsetPx / collapsedOffsetPx).coerceIn(0f, 1f)

        // Snap already-composed pages (e.g. adjacent in a pager) when the parent changes the
        // shared expand state. Skips the snap if the card is already in the right position.
        LaunchedEffect(initiallyExpanded) {
            val target = if (initiallyExpanded) CardDragValue.EXPANDED else CardDragValue.COLLAPSED
            if (state.currentValue != target) {
                state.snapTo(target)
            }
            savedExpanded = initiallyExpanded
        }

        LaunchedEffect(state.currentValue) {
            savedExpanded = state.currentValue == CardDragValue.EXPANDED
            onExpandChange(savedExpanded)
        }

        val nestedScrollConnection = remember(state) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    val delta = available.y
                    val currentOffset = if (state.offset.isNaN()) collapsedOffsetPx else state.offset
                    return if (delta < 0 && currentOffset > 0f) {
                        state.dispatchRawDelta(delta)
                        Offset(0f, delta)
                    } else {
                        Offset.Zero
                    }
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    val delta = available.y
                    return if (delta > 0 && source == NestedScrollSource.UserInput) {
                        Offset(0f, state.dispatchRawDelta(delta))
                    } else Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    val currentOffset = if (state.offset.isNaN()) collapsedOffsetPx else state.offset
                    if (available.y < 0f && currentOffset > 0f) {
                        state.settle(-1000f)
                        return available
                    }
                    return Velocity.Zero
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    val currentOffset = if (state.offset.isNaN()) collapsedOffsetPx else state.offset
                    if (currentOffset <= 0f || currentOffset >= collapsedOffsetPx) return Velocity.Zero

                    return when {
                        available.y > 0f -> {
                            // Downward fling: snap to COLLAPSED
                            state.settle(available.y)
                            available
                        }
                        available.y < 0f -> {
                            // Upward fling: snap to EXPANDED
                            state.settle(-1000f)
                            available
                        }
                        else -> {
                            // Slow stop after a collapse drag: settle by positional threshold
                            state.settle(0f)
                            Velocity.Zero
                        }
                    }
                }
            }
        }

        val topCornerRadiusDp = if (useMorphingCover) 0.dp else lerp(28f, 0f, expandFraction).dp
        val statusBarPx = with(density) { statusBarDp.toPx() }

        val outerBackground = if (useMorphingCover) backgroundColor else MaterialTheme.colorScheme.surface
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(outerBackground)
                .onGloballyPositioned { scaffoldCoordinates = it }
        ) {

            // Layer 1: Cover image display.
            // New UI 2 (morphing): no full-width image here — the morphing Card overlay below handles it.
            // New UI (current): full-width image that fades out as the card expands.
            if (!useMorphingCover) {
                ThumbnailImage(
                    data = coverData,
                    cacheKey = coverKey,
                    crossfade = !inSharedTransition,
                    usePlaceholderKey = false,
                    contentScale = ContentScale.Crop,
                    modifier = if (immersive)
                        Modifier
                            .then(coverSharedModifier)
                            .fillMaxWidth()
                            .offset { IntOffset(0, -statusBarPx.roundToInt()) }
                            .height(collapsedOffset + topCornerRadiusDp + statusBarDp)
                            .graphicsLayer { alpha = 1f - expandFraction }
                    else
                        Modifier
                            .then(coverSharedModifier)
                            .fillMaxWidth()
                            .height(collapsedOffset + topCornerRadiusDp)
                            .graphicsLayer { alpha = 1f - expandFraction }
                )
            }

            // Layer 2: Card — rendered in the SharedTransition overlay at z=0.5, above the
            // cover image (z=0 from sharedBounds default). This ensures the card is always
            // above the cover during the transition; after the transition, both return to
            // normal layout where card (drawn later) is naturally above the cover.
            val cardShape = RoundedCornerShape(topStart = topCornerRadiusDp, topEnd = topCornerRadiusDp)
            Column(
                modifier = Modifier
                    .then(cardOverlayModifier)
                    .offset { IntOffset(0, cardOffsetPx.roundToInt()) }
                    .fillMaxWidth()
                    .height(screenHeight)
                    .nestedScroll(nestedScrollConnection)
                    .anchoredDraggable(state, Orientation.Vertical)
                    .shadow(elevation = if (useMorphingCover) 0.dp else LocalCardShadowLevel.current.dp, shape = cardShape)
                    .clip(cardShape)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (immersiveColorEnabled) {
                        ThumbnailImage(
                            data = coverData,
                            cacheKey = coverKey,
                            contentScale = ContentScale.Crop,
                            placeholder = null,
                            modifier = Modifier
                                .matchParentSize()
                                .blur(radius = 40.dp, edgeTreatment = BlurredEdgeTreatment.Rectangle),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(scrimColor),
                    )
                    if (useMorphingCover) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .align(Alignment.TopCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(backgroundColor, Color.Transparent)
                                    )
                                )
                        )
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        if (!useMorphingCover) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 32.dp, height = 4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                )
                            }
                            Spacer(Modifier.height(lerp(0.dp, statusBarDp, expandFraction)))
                        } else {
                            // Edge-to-edge: card starts at y=0 (screen top). Grow a spacer from 0 → statusBarDp
                            // as the card expands so content clears the status bar when fully expanded.
                            Spacer(Modifier.height(lerp(0.dp, statusBarDp, expandFraction)))
                        }
                        Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            val onThumbnailPositioned: (LayoutCoordinates) -> Unit = { coords ->
                                scaffoldCoordinates?.let { root ->
                                    val absPos = root.localPositionOf(coords, Offset.Zero)
                                    // Normalize: subtract the card's current offset so we store the thumbnail's
                                    // position as it will be when the card is fully expanded (cardOffsetPx = 0).
                                    targetThumbnailOffset = Offset(absPos.x, absPos.y - cardOffsetPx)
                                }
                            }
                            val onTextPositioned: (LayoutCoordinates) -> Unit = { coords ->
                                scaffoldCoordinates?.let { root ->
                                    val absPos = root.localPositionOf(coords, Offset.Zero)
                                    targetTextOffset = Offset(absPos.x, absPos.y - cardOffsetPx)
                                }
                            }
                            cardContent(expandFraction, onThumbnailPositioned, onTextPositioned)
                        }
                    }
                }
            }

            // Layer 2.75: Morphing cover overlay (New UI 2 only) — the Card flies from full-screen
            // to the thumbnail position as the card expands. Disappears at expandFraction >= 0.99.
            if (useMorphingCover && expandFraction < 0.99f) {
                val cardWidthScale = LocalCardWidthScale.current
                val cardHeightScale = LocalCardHeightScale.current
                val targetWidth = thumbnailWidth * cardWidthScale
                val targetHeight = (thumbnailWidth * cardHeightScale) / ASPECT_RATIO
                val horizontalGap = (thumbnailWidth - targetWidth) / 2
                val verticalGap = (thumbnailWidth / ASPECT_RATIO - targetHeight) / 2

                val targetX = with(density) { targetThumbnailOffset.x.toDp() } + horizontalGap
                val targetY = with(density) { targetThumbnailOffset.y.toDp() } + verticalGap

                // UI2 uses edge-to-edge layout (content starts at y=0 behind status bar),
                // so no negative offset is needed — the cover starts flush with the screen top.
                val startY = 0.dp
                val startHeight = collapsedOffset

                val currentWidth = lerp(screenWidth, targetWidth, expandFraction)
                val currentHeight = lerp(startHeight, targetHeight, expandFraction)
                val currentX = lerp(0.dp, targetX, expandFraction)
                val currentY = lerp(startY, targetY, expandFraction)

                val currentTopRadius = lerp(0f, LocalCardCornerRadius.current, expandFraction).dp
                val currentBottomRadius = lerp(0f, LocalCardCornerRadius.current, expandFraction).dp
                val currentElevation = lerp(0f, LocalCardShadowLevel.current, expandFraction).dp

                val morphShape = RoundedCornerShape(
                    topStart = currentTopRadius, topEnd = currentTopRadius,
                    bottomStart = currentBottomRadius, bottomEnd = currentBottomRadius,
                )
                Box(
                    modifier = Modifier
                        .offset(x = currentX, y = currentY)
                        .size(width = currentWidth, height = currentHeight)
                        .shadow(elevation = currentElevation, shape = morphShape, clip = false)
                        .clip(morphShape)
                        .then(coverSharedModifier)
                ) {
                    ThumbnailImage(
                        data = coverData,
                        cacheKey = coverKey,
                        crossfade = !inSharedTransition,
                        usePlaceholderKey = false,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Gradient merges the bottom of the cover into the card below
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.65f)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, backgroundColor)
                                )
                            )
                    )
                }

                if (heroTextContent != null) {
                    val targetTextX = with(density) { targetTextOffset.x.toDp() }
                    val targetTextY = with(density) { targetTextOffset.y.toDp() }

                    val startTextY = collapsedOffset - heroTextHeight - 24.dp
                    val currentTextX = lerp(0.dp, targetTextX, expandFraction)
                    val currentTextY = lerp(startTextY, targetTextY, expandFraction)

                    Box(
                        modifier = Modifier
                            .offset(x = currentTextX, y = currentTextY)
                            .fillMaxWidth()
                            .onGloballyPositioned { heroTextHeight = with(density) { it.size.height.toDp() } }
                            .graphicsLayer(alpha = if (heroTextHeight > 0.dp) 1f else 0f)
                    ) {
                        heroTextContent(expandFraction)
                    }
                }
            }

            // Layer 3: Top bar
            Box(modifier = Modifier.fillMaxWidth().then(uiEnterExitModifier).statusBarsPadding()) {
                topBarContent()
            }

            // Layer 3.5: caller-supplied screen-anchored overlay. Same enter/exit
            // treatment as the top bar so it travels with the UI rather than with
            // the card during a shared transition.
            Box(modifier = Modifier.fillMaxSize().then(uiEnterExitModifier)) {
                overlayContent()
            }
        }

        // Layer 4: FAB — in overlay at z=1, above everything
        val extraBottomPadding = LocalTransparentNavBarPadding.current
        val useFloatingNavigationBar = LocalUseFloatingNavigationBar.current
        if (useFloatingNavigationBar) {
            fabContent()
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .then(fabOverlayModifier)
                    .then(if (extraBottomPadding == 0.dp) Modifier.windowInsetsPadding(WindowInsets.navigationBars) else Modifier)
                    .padding(bottom = 16.dp + extraBottomPadding)
            ) {
                fabContent()
            }
        }
    }
}
