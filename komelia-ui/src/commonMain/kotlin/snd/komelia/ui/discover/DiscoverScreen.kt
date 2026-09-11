package snd.komelia.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import snd.komelia.discover.DiscoverSuggestion
import snd.komelia.ui.LoadState
import snd.komelia.ui.LocalLibraries
import snd.komelia.ui.LocalRawStatusBarHeight
import snd.komelia.ui.LocalStrings
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.common.components.ErrorContent
import snd.komelia.ui.common.components.LoadingMaxSizeIndicator

/**
 * Series the user does NOT own, suggested from the ones they do.
 *
 * A separate tab on purpose, and never mixed into Home or a library: everything
 * else in the app is the user's own catalogue, and a card here is something to
 * go and buy. It is also the only screen backed by a third party, which the
 * user opts into.
 *
 * The screen makes no network request of its own — it reads the table
 * [DiscoverScanner] fills in the background.
 */
class DiscoverScreen : Screen {
    override val key: String = "discover"

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel { viewModelFactory.getDiscoverViewModel() }
        val libraries = LocalLibraries.current.collectAsState().value
        LaunchedEffect(libraries) {
            if (libraries.isNotEmpty()) vm.initialize(libraries.map { it.id })
        }

        val uriHandler = LocalUriHandler.current
        val statusBarHeight = LocalRawStatusBarHeight.current
        val scanning = vm.scanning.collectAsState().value
        val sourceNames = vm.sourceNames.collectAsState().value

        Column(Modifier.fillMaxSize().padding(top = statusBarHeight)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { navigator.pop() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = LocalStrings.current.ui.back)
                }
                Icon(Icons.Rounded.Explore, contentDescription = null, modifier = Modifier.padding(start = 4.dp))
                Text(
                    "Découvertes",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 12.dp).weight(1f),
                )
                IconButton(
                    onClick = { vm.refresh(libraries.map { it.id }) },
                    enabled = !scanning && libraries.isNotEmpty(),
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Actualiser")
                }
            }

            if (scanning) {
                val progress = vm.progress.collectAsState().value
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }

            // Two lists, one screen. Kept cards leave the suggestion list, so
            // without somewhere to go the gesture would look like a dismissal.
            val interested = vm.interested.collectAsState().value
            val hideUnlicensed = vm.hideUnlicensed.collectAsState().value
            var showKept by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (interested.isNotEmpty()) {
                    FilterChip(
                        selected = !showKept,
                        onClick = { showKept = false },
                        label = { Text("Suggestions") },
                    )
                    FilterChip(
                        selected = showKept,
                        onClick = { showKept = true },
                        label = { Text("Intéressé (${interested.size})") },
                    )
                }
                // Opt-in on purpose: with no French signal available, this
                // hides series that may well exist in French.
                if (!showKept) {
                    FilterChip(
                        selected = hideUnlicensed,
                        onClick = { vm.setHideUnlicensed(!hideUnlicensed) },
                        label = { Text("Masquer sans édition anglaise") },
                    )
                }
            }

            when (val state = vm.state.collectAsState().value) {
                is LoadState.Error -> ErrorContent(
                    message = state.exception.message ?: "Unknown Error",
                    onReload = { vm.refresh(libraries.map { it.id }) },
                )

                LoadState.Uninitialized, LoadState.Loading -> LoadingMaxSizeIndicator()

                is LoadState.Success -> {
                    val shown = when {
                        showKept -> interested
                        hideUnlicensed -> state.value.filter { it.licensed }
                        else -> state.value
                    }
                    if (shown.isEmpty()) {
                        Text(
                            when {
                                showKept -> "Rien de gardé pour l'instant."
                                scanning -> "Recherche en cours…"
                                hideUnlicensed && state.value.isNotEmpty() ->
                                    "Toutes les suggestions sont sans édition anglaise connue. Désactivez le filtre pour les voir."
                                else -> "Aucune suggestion pour l'instant. Notez ou mettez en favori quelques séries, puis actualisez."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(shown, key = { it.externalId }) { suggestion ->
                                SuggestionCard(
                                    suggestion = suggestion,
                                    sourceNames = sourceNames,
                                    onOpen = { if (suggestion.url.isNotBlank()) uriHandler.openUri(suggestion.url) },
                                    onDismiss = { vm.dismiss(suggestion) },
                                    onToggleInterested = {
                                        vm.setInterested(suggestion, !suggestion.interested)
                                    },
                                    onSearchFrench = { uriHandler.openUri(nautiljonSearchUrl(suggestion.title)) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Nautiljon's search, pre-filled. The French edition cannot be read from any
 * free source (Nautiljon itself refuses robots), so the card puts the answer
 * one tap away instead of guessing.
 */
private fun nautiljonSearchUrl(title: String): String =
    "https://www.nautiljon.com/mangas/?q=" + title.encodeUrlQuery()

private fun String.encodeUrlQuery(): String = buildString {
    for (byte in this@encodeUrlQuery.encodeToByteArray()) {
        val c = byte.toInt() and 0xFF
        val ch = c.toChar()
        if (ch.isLetterOrDigit() && c < 128 || ch == '-' || ch == '_' || ch == '.' || ch == '~') append(ch)
        else if (ch == ' ') append('+')
        else append('%').append(c.toString(16).uppercase().padStart(2, '0'))
    }
}

@Composable
private fun SuggestionCard(
    suggestion: DiscoverSuggestion,
    sourceNames: Map<String, String>,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onToggleInterested: () -> Unit,
    onSearchFrench: () -> Unit,
) {
    // Collapsed by default: the summaries run several paragraphs, and a page of
    // them is unreadable. Per-card, and deliberately not remembered — this is a
    // list to skim, not a state to maintain.
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(Modifier.width(70.dp).height(105.dp).clip(RoundedCornerShape(4.dp))) {
                    if (suggestion.imageUrl.isNotBlank()) {
                        AsyncImage(
                            model = suggestion.imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        suggestion.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    // Year, publication state and rating on one line: the three
                    // things that decide whether a series is worth a second
                    // look, and they fit.
                    val facts = buildList {
                        if (suggestion.year.isNotBlank()) add(suggestion.year)
                        if (suggestion.status.isNotBlank()) add(suggestion.status)
                    }
                    if (facts.isNotEmpty()) {
                        Text(
                            facts.joinToString("  ·  "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // A rating without its sample size is a number pretending to
                    // be evidence: 8.7 over 4350 votes is not 8.7 over 2.
                    if (suggestion.rating > 0.0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Star,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                buildString {
                                    append(" ")
                                    append(formatRating(suggestion.rating))
                                    if (suggestion.ratingVotes > 0) append(" (${suggestion.ratingVotes} votes)")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (suggestion.authors.isNotEmpty()) {
                        Text(
                            suggestion.authors.take(3).joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // Only names that resolved: a raw series id on a card would
                    // be noise, and a missing one is not worth an error.
                    // The one availability the source can answer. It tracks
                    // Original and English publishers and nothing else, so
                    // "VO uniquement" means "no English edition known" -- a
                    // French one is neither confirmed nor ruled out, hence the
                    // Nautiljon button below rather than a French badge.
                    Text(
                        if (suggestion.licensed) {
                            val by = suggestion.englishPublishers.joinToString(", ")
                            if (by.isBlank()) "Édition anglaise" else "Édition anglaise : $by"
                        } else {
                            "VO uniquement (aucune édition anglaise connue)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (suggestion.licensed) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )

                    val because = suggestion.becauseOf.mapNotNull { sourceNames[it] }
                    if (because.isNotEmpty()) {
                        Text(
                            "Parce que vous lisez ${because.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onToggleInterested) {
                        Icon(
                            if (suggestion.interested) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                            contentDescription = if (suggestion.interested) "Retirer des intéressés" else "Intéressé",
                            tint = if (suggestion.interested) MaterialTheme.colorScheme.primary
                            else LocalContentColor.current,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Ne plus proposer")
                    }
                }
            }

            if (suggestion.genres.isNotEmpty()) {
                Text(
                    suggestion.genres.take(6).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (suggestion.description.isNotBlank()) {
                Text(
                    suggestion.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable { expanded = !expanded },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onSearchFrench) {
                    Text("Chercher en VF")
                }
                TextButton(onClick = onOpen, enabled = suggestion.url.isNotBlank()) {
                    Text("Voir la fiche")
                }
            }
        }
    }
}

/** One decimal, without pulling in a formatter for a single number. */
private fun formatRating(rating: Double): String {
    val rounded = kotlin.math.round(rating * 10) / 10.0
    return rounded.toString().take(3)
}
