package am.noz.caelum.ui.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.MoodBad
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import am.noz.caelum.core.i18n.tr
import am.noz.caelum.ui.components.LocalAppContainer
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private data class GifCategory(val query: String, val label: String, val icon: ImageVector)

private val gifCategories = listOf(
    GifCategory("", "Trending GIFs", Icons.Filled.TrendingUp),
    GifCategory("love", "Love GIFs", Icons.Filled.FavoriteBorder),
    GifCategory("happy", "Happy GIFs", Icons.Filled.Mood),
    GifCategory("cry", "Sad GIFs", Icons.Filled.MoodBad),
    GifCategory("congratulations", "Party GIFs", Icons.Filled.Celebration),
    GifCategory("yes", "Yes GIFs", Icons.Filled.ThumbUp),
)

@Composable
internal fun ComposerGifPicker(
    onSelect: (ComposerAttachment.Gif) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(gifCategories.first()) }
    val searchLabel = tr("Search KLIPY")
    val query = input.trim().ifEmpty { category.query }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = tr("Close"))
                    }
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("composerGifSearch")
                            .semantics { contentDescription = searchLabel },
                        placeholder = { Text(searchLabel) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = if (input.isNotEmpty()) {
                            {
                                IconButton(onClick = {
                                    input = ""
                                    category = gifCategories.first()
                                }) {
                                    Icon(Icons.Filled.Close, contentDescription = tr("Clear search query"))
                                }
                            }
                        } else null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        shape = RoundedCornerShape(12.dp),
                    )
                }

                if (input.isBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        for (item in gifCategories) {
                            FilledTonalIconToggleButton(
                                checked = category == item,
                                onCheckedChange = { category = item },
                            ) {
                                Icon(item.icon, contentDescription = tr(item.label))
                            }
                        }
                    }
                }
                key(query) {
                    GifResults(query, onSelect)
                }
            }
        }
    }
}

@Composable
private fun GifResults(
    query: String,
    onSelect: (ComposerAttachment.Gif) -> Unit,
) {
    val container = LocalAppContainer.current
    var gifs by remember { mutableStateOf(emptyList<ComposerAttachment.Gif>()) }
    var cursor by remember { mutableStateOf<String?>(null) }
    var next by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var failure by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(query, cursor, retry) {
        loading = true
        failure = false
        try {
            if (cursor == null && query.isNotEmpty() && retry == 0) delay(350)
            val page = container.findComposerGifs(query, cursor)
            gifs = (gifs + page.results).distinctBy { it.uri }
            next = page.next?.takeIf { it != cursor }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            failure = true
        } finally {
            loading = false
        }
    }

    if (gifs.isEmpty() && (loading || failure || next == null)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (loading) {
                CircularProgressIndicator()
            } else {
                Text(
                    text = when {
                        failure -> tr("There was a problem loading GIFs. Check your connection and try again.")
                        query.isEmpty() -> tr("No GIFs to show right now. Try again in a moment.")
                        else -> tr("No GIFs found for \"{query}\".", mapOf("query" to query))
                    },
                    textAlign = TextAlign.Center,
                )
                if (failure || query.isEmpty()) {
                    TextButton(onClick = { retry++ }) { Text(tr("Try again")) }
                }
            }
        }
        return
    }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(144.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp,
        modifier = Modifier.fillMaxSize(),
    ) {
        items(gifs, key = { it.uri }) { gif ->
            val label = if (gif.title.isBlank()) tr("Select GIF") else tr("Select GIF \"{0}\"", mapOf("0" to gif.title))
            val aspect = remember(gif.uri) {
                val url = gif.uri.toHttpUrlOrNull()
                val width = url?.queryParameter("ww")?.toFloatOrNull() ?: 1f
                val height = url?.queryParameter("hh")?.toFloatOrNull() ?: 1f
                (width / height).coerceIn(0.4f, 3f)
            }
            AsyncImage(
                model = gif.previewUri,
                imageLoader = container.imageLoader,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(role = Role.Button) { onSelect(gif) }
                    .semantics { contentDescription = label },
            )
        }

        if (loading || failure || next != null) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        loading -> CircularProgressIndicator()
                        failure -> TextButton(onClick = { retry++ }) { Text(tr("Try again")) }
                        else -> TextButton(onClick = { cursor = next }) { Text(tr("Load more")) }
                    }
                }
            }
        }
    }
}
