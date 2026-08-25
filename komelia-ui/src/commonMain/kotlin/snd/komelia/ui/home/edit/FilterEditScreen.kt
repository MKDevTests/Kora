package snd.komelia.ui.home.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.launch
import snd.komelia.ui.LoadState
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.common.components.ErrorContent
import snd.komelia.ui.home.HomeFilterData
import snd.komelia.ui.home.HomeScreen
import snd.komelia.ui.home.edit.view.FilterEditContent
import kotlin.jvm.Transient

class FilterEditScreen(
    // FIXME should be serializable
    @Transient
    private val homeFilters: List<HomeFilterData>? = null
) : Screen {

    /**
     * Unique per instance, because two instances can legitimately be alive at
     * the same time and Voyager's default key cannot tell them apart.
     *
     * The edit button is a floating action button hoisted out of HomeScreen
     * into a global slot, and that slot is only cleared by HomeScreen's
     * onDispose — which runs *after* the navigation transition. So the button
     * stays on screen and stays clickable while the transition is running, and
     * a second tap builds a second FilterEditScreen while the first is still
     * composed. Both then land in the same SaveableStateProvider under the
     * default key `snd.komelia.ui.home.edit.FilterEditScreen:screen`, and it
     * throws "Key ... was used multiple times" — a hard crash, reported from
     * the tablet on 2026-08-25 while re-enabling home shelves.
     *
     * The tap is also guarded at the source (see HomeScreen), but the guard is
     * a race and this is not: no two instances can ever collide again.
     *
     * Every other Screen in the app overrides `key`; this one was the
     * exception. Losing state restoration across process death costs nothing
     * here — `homeFilters` is @Transient, so it was never restorable anyway.
     */
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel { viewModelFactory.getFilterEditViewModel(homeFilters) }
        val navigator = LocalNavigator.currentOrThrow
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            vm.initialize()
        }

        when (val state = vm.state.collectAsState().value) {
            is LoadState.Error -> ErrorContent(
                message = state.exception.message ?: "Unknown Error",
                onExit = { navigator.replaceAll(HomeScreen()) }
            )

            else -> FilterEditContent(
                filters = vm.filters.collectAsState().value,
                onFilterMove = vm::onFilterReorder,
                onEditEnd = {
                    coroutineScope.launch {
                        vm.onEditEnd()
                        navigator.replaceAll(HomeScreen())
                    }
                },
                onFilterAdd = vm::onFilterAdd,
                onFilterRemove = vm::onFilterRemove,
                onFiltersReset = vm::onResetFiltersToDefault
            )
        }
    }
}