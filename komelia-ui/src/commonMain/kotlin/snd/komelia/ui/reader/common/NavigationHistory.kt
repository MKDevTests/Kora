package snd.komelia.ui.reader.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class NavigationSource {
    SLIDER,
    CAROUSEL,
    NOTES,
    BACK_BUTTON
}

interface NavigationLocation

data class ImagePageLocation(val page: Int) : NavigationLocation

data class NavigationEntry(
    val source: NavigationSource,
    val location: NavigationLocation
)

class NavigationHistory {
    private val _history = MutableStateFlow<List<NavigationEntry>>(emptyList())
    val history: StateFlow<List<NavigationEntry>> = _history.asStateFlow()

    private val _buttonVisible = MutableStateFlow(false)
    val buttonVisible: StateFlow<Boolean> = _buttonVisible.asStateFlow()

    fun addEntry(source: NavigationSource, location: NavigationLocation) {
        _history.update { it + NavigationEntry(source, location) }
        if (source != NavigationSource.BACK_BUTTON) {
            _buttonVisible.value = true
        } else {
            _buttonVisible.value = false
        }
    }

    fun popEntry(): NavigationEntry? {
        val currentHistory = _history.value
        if (currentHistory.isEmpty()) return null

        val last = currentHistory.last()
        _history.update { it.dropLast(1) }
        _buttonVisible.value = false
        return last
    }

    fun dismissBackButton() {
        _buttonVisible.value = false
    }

    fun clear() {
        _history.value = emptyList()
        _buttonVisible.value = false
    }
}
