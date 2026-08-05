package snd.komelia.ui.common

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuScope
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import snd.komelia.ui.LocalStrings

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SplitFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    primaryActionText: String,
    primaryActionIcon: ImageVector,
    onPrimaryActionClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    menuItems: @Composable FloatingActionButtonMenuScope.() -> Unit,
) {
    FloatingActionButtonMenu(
        expanded = expanded,
        button = {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = containerColor,
                contentColor = contentColor,
                shadowElevation = 6.dp,
                modifier = Modifier.animateContentSize()
            ) {
                if (expanded) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onExpandedChange(false) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = LocalStrings.current.ui.closeMenu,
                            tint = contentColor
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.height(56.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left side for primary action
                        Row(
                            modifier = Modifier
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                                .clickable { onPrimaryActionClick() }
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(primaryActionIcon, contentDescription = null, tint = contentColor)
                            Spacer(Modifier.width(8.dp))
                            Text(primaryActionText, color = contentColor)
                        }

                        // Right side for toggle
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                                .clickable { onExpandedChange(true) }
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.KeyboardArrowUp,
                                contentDescription = LocalStrings.current.ui.openMenu,
                                tint = contentColor
                            )
                        }
                    }
                }
            }
        },
        modifier = modifier
    ) {
        menuItems()
    }
}
