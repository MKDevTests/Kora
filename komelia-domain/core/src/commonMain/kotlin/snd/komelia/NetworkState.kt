package snd.komelia

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Whether the device currently has a physical network (Wi-Fi, mobile), as
 * the platform reports it.
 *
 * Android feeds this from a ConnectivityManager callback on non-VPN
 * networks; other targets never touch it and stay on the [true] default,
 * which is the behaviour they had before this existed.
 *
 * [comebacks] counts the arrivals of a physical network. A reader that failed
 * pages while the link was down, or a read-progress push that could not be
 * delivered, waits on it rather than on a timer: the moment the link is back
 * is the moment worth asking again. Measured on 2026-09-13: after the tablet
 * wakes, the Wi-Fi comes back on its own about a minute later — and the
 * platform announces it.
 *
 * Physical, not default: with a VPN up the VPN is the app's default network
 * for good (the requests leave from its address and cannot reach the LAN),
 * so the default-network callback never fires for a Wi-Fi cut or return —
 * yet the Wi-Fi coming back is exactly the moment to ask again. Measured on
 * 2026-09-13. Every arrival counts, whatever the state before it.
 */
object NetworkState {
    private val _isAvailable = MutableStateFlow(true)
    val isAvailable: StateFlow<Boolean> = _isAvailable

    private val _comebacks = MutableStateFlow(0)
    val comebacks: StateFlow<Int> = _comebacks

    /** A physical network arrived (a new one, or the first after none). */
    fun networkArrived() {
        _isAvailable.value = true
        _comebacks.update { it + 1 }
    }

    /** No physical network left. */
    fun networkLost() {
        _isAvailable.value = false
    }
}
