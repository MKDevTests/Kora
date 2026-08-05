package snd.komelia.ui.common.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyGridState
import snd.komelia.ui.LocalCardCornerRadius
import snd.komelia.ui.LocalCardHeightScale
import snd.komelia.ui.LocalCardLayoutBelow
import snd.komelia.ui.LocalCardLayoutOverlayBackground
import snd.komelia.ui.LocalCardShadowLevel
import snd.komelia.ui.LocalCardSpacingBelow
import snd.komelia.ui.LocalCardWidthScale
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.LocalUseNewLibraryUI2
import snd.komelia.ui.common.ThumbnailConstants.ASPECT_RATIO
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.platform.cursorForHand
import snd.komelia.ui.LocalStrings

const val defaultCardWidth = 240
const val DEFAULT_CARD_MAX_LINES = 2

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryItemCard(
    modifier: Modifier = Modifier,
    title: String,
    secondaryText: String? = null,
    secondaryTextTop: Boolean = false,
    isUnavailable: Boolean = false,
    titleBold: Boolean = false,
    showText: Boolean = true,
    fillMaxWidth: Boolean = true,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    badges: @Composable BoxScope.() -> Unit = {},
    progress: @Composable BoxScope.() -> Unit = {},
    image: @Composable () -> Unit,
) {
    val cardLayoutBelow = LocalCardLayoutBelow.current
    val overlayBackground = LocalCardLayoutOverlayBackground.current
    val cardWidthScale = LocalCardWidthScale.current
    val cardHeightScale = LocalCardHeightScale.current
    val cardSpacingBelow = LocalCardSpacingBelow.current
    val cornerRadius = LocalCardCornerRadius.current
    val shadowLevel = LocalCardShadowLevel.current

    val shape = RoundedCornerShape(cornerRadius.dp)
    val color = if (cardLayoutBelow) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant
    val cardRatio = ASPECT_RATIO * (cardWidthScale / cardHeightScale)

    Column(
        modifier = modifier.padding(bottom = (defaultCardWidth * cardSpacingBelow).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Card(
            shape = shape,
            modifier = Modifier
                .then(if (fillMaxWidth) Modifier.fillMaxWidth(cardWidthScale) else Modifier)
                .aspectRatio(cardRatio, matchHeightConstraintsFirst = true)
                .combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick)
                .then(if (onClick != null || onLongClick != null) Modifier.cursorForHand() else Modifier)
                .shadow(elevation = shadowLevel.dp, shape = shape),
            colors = CardDefaults.cardColors(containerColor = color),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            // Thumbnail Logic
            val imageShape = RoundedCornerShape(cornerRadius.dp)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(imageShape)
            ) {
                image()
                badges()

                // Gradients and overlay text — centralized here
                if (!cardLayoutBelow && !overlayBackground) CardTopGradient()
                if (!cardLayoutBelow && showText) {
                    Box(
                        contentAlignment = Alignment.BottomStart,
                        modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    ) {
                        CardTextBackground()
                        Column(
                            modifier = Modifier
                                .height(if (overlayBackground) 38.dp else 48.dp)
                                .padding(
                                    start = 8.dp,
                                    end = 8.dp,
                                    top = if (overlayBackground) 2.dp else 4.dp,
                                    bottom = 4.dp
                                ),
                            verticalArrangement = if (overlayBackground) Arrangement.Top else Arrangement.Bottom
                        ) {
                            val textColor = if (overlayBackground) MaterialTheme.colorScheme.onSurface else Color.White
                            val secondaryTextColor = if (overlayBackground) MaterialTheme.colorScheme.onSurfaceVariant else Color.White.copy(alpha = 0.8f)
                            val shadow = if (overlayBackground) null else Shadow(color = Color.Black, offset = Offset(1f, 1f), blurRadius = 4f)

                            val useNewLibraryUI2 = LocalUseNewLibraryUI2.current
                            val primaryStyle = MaterialTheme.typography.bodySmall.copy(
                                shadow = shadow,
                                fontWeight = FontWeight.Bold,
                            )
                            val secondaryStyle = MaterialTheme.typography.labelSmall.copy(
                                shadow = shadow,
                                fontWeight = FontWeight.Normal,
                            )

                            if (isUnavailable) {
                                Text(LocalStrings.current.ui.unavailable, style = primaryStyle, color = MaterialTheme.colorScheme.error, maxLines = 1)
                                Text(title, style = primaryStyle, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            } else {
                                val secondary = @Composable {
                                    if (secondaryText != null) {
                                        val text = if (useNewLibraryUI2) secondaryText.uppercase() else secondaryText
                                        Text(text, style = secondaryStyle, color = secondaryTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                val primary = @Composable {
                                    Text(title, style = primaryStyle, color = textColor, maxLines = if (secondaryText == null) 2 else 1, overflow = TextOverflow.Ellipsis)
                                }

                                if (secondaryTextTop) { secondary(); primary() } else { primary(); secondary() }
                            }
                        }
                    }
                }
                progress() // Always rendered on the image
            }
        }

        // Below Card Text Logic
        if (cardLayoutBelow && showText) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(cardWidthScale)
                    .padding(top = 4.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.Center
            ) {
                val useNewLibraryUI2 = LocalUseNewLibraryUI2.current
                val primaryStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                val secondaryStyle = MaterialTheme.typography.labelSmall

                if (isUnavailable) {
                    Text(LocalStrings.current.ui.unavailable, style = primaryStyle, color = MaterialTheme.colorScheme.error, maxLines = 1)
                    Text(title, style = primaryStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    val secondary = @Composable {
                        if (secondaryText != null) {
                            val text = if (useNewLibraryUI2) secondaryText.uppercase() else secondaryText
                            Text(text, style = secondaryStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    val primary = @Composable {
                        Text(
                            text = title,
                            style = primaryStyle,
                            maxLines = if (secondaryText == null) 2 else 1,
                            minLines = if (secondaryText == null) 2 else 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (secondaryTextTop) { secondary(); primary() } else { primary(); secondary() }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ItemCard(
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    showText: Boolean = true,
    fillMaxWidth: Boolean = true,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    image: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val cardLayoutBelow = LocalCardLayoutBelow.current
    val color = containerColor ?: if (cardLayoutBelow) Color.Transparent
    else MaterialTheme.colorScheme.surfaceVariant

    val cardWidthScale = LocalCardWidthScale.current
    val cardHeightScale = LocalCardHeightScale.current
    val cardSpacingBelow = LocalCardSpacingBelow.current
    val cornerRadius = LocalCardCornerRadius.current
    val shadowLevel = LocalCardShadowLevel.current

    val shape = RoundedCornerShape(cornerRadius.dp)
    val cardRatio = ASPECT_RATIO * (cardWidthScale / cardHeightScale)

    Column(
        modifier = modifier.padding(bottom = (defaultCardWidth * cardSpacingBelow).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Card(
            shape = shape,
            modifier = Modifier
                .then(if (fillMaxWidth) Modifier.fillMaxWidth(cardWidthScale) else Modifier)
                .aspectRatio(cardRatio, matchHeightConstraintsFirst = true)
                .combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick)
                .then(if (onClick != null || onLongClick != null) Modifier.cursorForHand() else Modifier)
                .shadow(elevation = shadowLevel.dp, shape = shape),
            colors = CardDefaults.cardColors(containerColor = color),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            val imageShape = RoundedCornerShape(cornerRadius.dp)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(imageShape)
            ) { image() }
            if (!cardLayoutBelow && showText) content()
        }
        if (cardLayoutBelow && showText) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(cardWidthScale)
                    .padding(top = 4.dp, bottom = 4.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
fun ItemCardWithContent(
    modifier: Modifier = Modifier,
    fillMaxWidth: Boolean = true,
    image: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardLayoutBelow = LocalCardLayoutBelow.current
    val cardWidthScale = LocalCardWidthScale.current
    val cardHeightScale = LocalCardHeightScale.current
    val cardSpacingBelow = LocalCardSpacingBelow.current
    val cornerRadius = LocalCardCornerRadius.current
    val shadowLevel = LocalCardShadowLevel.current

    val shape = RoundedCornerShape(cornerRadius.dp)
    val cardRatio = ASPECT_RATIO * (cardWidthScale / cardHeightScale)

    Column(
        modifier = modifier.padding(bottom = (defaultCardWidth * cardSpacingBelow).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Card(
            shape = shape,
            modifier = Modifier
                .then(if (fillMaxWidth) Modifier.fillMaxWidth(cardWidthScale) else Modifier)
                .aspectRatio(cardRatio, matchHeightConstraintsFirst = true)
                .shadow(elevation = shadowLevel.dp, shape = shape),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) { image() }
            if (!cardLayoutBelow) content()
        }
        if (cardLayoutBelow) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(cardWidthScale)
                    .padding(top = 4.dp, bottom = 4.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
fun CardTextBackground(modifier: Modifier = Modifier) {
    val overlayBackground = LocalCardLayoutOverlayBackground.current
    if (overlayBackground) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(38.dp) // Reduced by 20%
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
        )
    } else {
        CardBottomGradient(modifier)
    }
}

@Composable
fun CardTopGradient() {
    Box(
        Modifier.fillMaxWidth().height(40.dp)
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent)))
    )
}

@Composable
fun CardBottomGradient(modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(80.dp)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
    )
}

@Composable
fun overlayBorderModifier() =
    Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.tertiary), RoundedCornerShape(5.dp))


@Composable
fun SelectionRadioButton(
    isSelected: Boolean,
    onSelect: () -> Unit,
) {

    RadioButton(
        selected = isSelected,
        onClick = onSelect,
        colors = RadioButtonDefaults.colors(
            selectedColor = MaterialTheme.colorScheme.tertiary,
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(topEnd = 17.dp, bottomEnd = 17.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .4f))
            .selectable(selected = isSelected, onClick = onSelect)
    )
}

@Composable
fun LazyGridItemScope.DraggableImageCard(
    key: String,
    dragEnabled: Boolean,
    reorderableState: ReorderableLazyGridState,
    content: @Composable () -> Unit
) {
    val platform = LocalPlatform.current
    if (dragEnabled) {
        ReorderableItem(reorderableState, key = key) {
            if (platform == PlatformType.MOBILE) {

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    content()
                    Box(
                        modifier = Modifier
                            .height(30.dp)
                            .fillMaxWidth()
                            .draggableHandle()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.DragHandle, null) }
                }

            } else {
                Box(Modifier.draggableHandle()) { content() }
            }

        }
    } else content()
}

@Composable
fun IndicatorBadge(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
    borderColor: Color = MaterialTheme.colorScheme.background,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .padding(1.dp)
            .defaultMinSize(minWidth = 24.dp, minHeight = 24.dp)
            .background(backgroundColor, CircleShape)
            .border(1.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center,
        content = content
    )
}
