package snd.komelia.ui.discover

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

            when (val state = vm.state.collectAsState().value) {
                is LoadState.Error -> ErrorContent(
                    message = state.exception.message ?: "Unknown Error",
                    onReload = { vm.refresh(libraries.map { it.id }) },
                )

                LoadState.Uninitialized, LoadState.Loading -> LoadingMaxSizeIndicator()

                is LoadState.Success -> {
                    if (state.value.isEmpty()) {
                        Text(
                            if (scanning) "Recherche en cours…"
                            else "Aucune suggestion pour l'instant. Notez ou mettez en favori quelques séries, puis actualisez.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(state.value, key = { it.externalId }) { suggestion ->
                                SuggestionCard(
                                    suggestion = suggestion,
                                    sourceNames = sourceNames,
                                    onOpen = { if (suggestion.url.isNotBlank()) uriHandler.openUri(suggestion.url) },
                                    onDismiss = { vm.dismiss(suggestion) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    suggestion: DiscoverSuggestion,
    sourceNames: Map<String, String>,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(60.dp).height(90.dp).clip(RoundedCornerShape(4.dp))) {
                if (suggestion.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = suggestion.imageUrl,
                        contentDescription = null,
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
                // Only names that resolved: a raw series id on a card would be
                // noise, and a missing one is not worth an error.
                val because = suggestion.becauseOf.mapNotNull { sourceNames[it] }
                if (because.isNotEmpty()) {
                    Text(
                        "Parce que vous lisez ${because.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = "Ne plus proposer")
            }
        }
    }
}
