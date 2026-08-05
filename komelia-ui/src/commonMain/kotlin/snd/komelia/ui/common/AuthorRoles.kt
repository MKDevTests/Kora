package snd.komelia.ui.common

import snd.komga.client.common.KomgaAuthor
import snd.komga.client.common.coloristRole
import snd.komga.client.common.coverRole
import snd.komga.client.common.editorRole
import snd.komga.client.common.inkerRole
import snd.komga.client.common.lettererRole
import snd.komga.client.common.pencillerRole
import snd.komga.client.common.translatorRole
import snd.komga.client.common.writerRole

/**
 * The credits Komga knows, most significant first.
 *
 * Komga fills up to eight of them and the book page prints one row per role,
 * which is a lot of screen for names most readers don't look up. The user picks
 * which ones to keep in Settings → Appearance; see [LocalHiddenAuthorRoles].
 */
val authorRolesOrder = listOf(
    writerRole,
    pencillerRole,
    inkerRole,
    coloristRole,
    lettererRole,
    coverRole,
    editorRole,
    translatorRole,
)

/**
 * "writer" -> "Scénario". Komga stores roles lowercase.
 *
 * The eight roles Komga fills are named; anything else it may add later falls
 * back to the capitalised raw value, which is still readable.
 */
@androidx.compose.runtime.Composable
fun authorRoleLabel(role: String): String {
    val roles = snd.komelia.ui.LocalStrings.current.roles
    return when (role.lowercase()) {
        "writer" -> roles.writer
        "penciller" -> roles.penciller
        "inker" -> roles.inker
        "colorist" -> roles.colorist
        "letterer" -> roles.letterer
        "cover" -> roles.cover
        "editor" -> roles.editor
        "translator" -> roles.translator
        else -> role.replaceFirstChar { it.uppercase() }
    }
}

/**
 * Drops the credits whose role the user hid.
 *
 * [hidden] is null when the filter is off — every role is shown, exactly as
 * before the setting existed. Null and empty are deliberately different: an
 * empty set means "filter on, nothing hidden", which the series screens read as
 * "show every role" rather than falling back to their historical short list.
 */
fun List<KomgaAuthor>.withoutHiddenRoles(hidden: Set<String>?): List<KomgaAuthor> =
    if (hidden.isNullOrEmpty()) this
    else filterNot { it.role.lowercase() in hidden }
