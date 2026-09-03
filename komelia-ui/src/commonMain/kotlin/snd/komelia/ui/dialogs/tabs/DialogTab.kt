package snd.komelia.ui.dialogs.tabs

import androidx.compose.runtime.Composable

interface DialogTab {
    // Composable so a tab title can come from the catalogue. Every call site is
    // already inside a composition (TabNavigationItems), so this costs nothing.
    @Composable
    fun options(): TabItem

    @Composable
    fun Content()
}