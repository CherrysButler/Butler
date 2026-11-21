package com.cherry.butler.feature.lorebook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cherry.butler.core.design.PlateLevel
import com.cherry.butler.core.design.PlateText
import com.cherry.butler.core.design.SheetShape
import com.cherry.butler.ui.components.SheetHandle
import com.cherry.butler.ui.components.SheetScrim

/** One library shared by chat switches and persona bindings; a new persona keeps a draft selection. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LorebooksSheet(
    scope: String?,
    onDismiss: () -> Unit,
    onEdit: (String?) -> Unit,
    inheritedPersonaScope: String? = null,
    selection: Set<String>? = null,
    onSelectionChange: ((Set<String>) -> Unit)? = null,
    viewModel: LorebooksSheetViewModel = hiltViewModel(),
) {
    val booksFlow = remember(viewModel, scope, inheritedPersonaScope) { viewModel.booksFor(scope, inheritedPersonaScope) }
    val allBooks by booksFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    val books = allBooks.filter { !favoritesOnly || it.favorite }
    fun toggle(id: String, on: Boolean) {
        if (onSelectionChange != null) {
            val selected = selection.orEmpty()
            onSelectionChange(if (on) selected + id else selected - id)
        } else if (scope != null) viewModel.setEnabled(scope, id, on)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = SheetScrim,
        dragHandle = { SheetHandle() },
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                PlateText("Local Lorebooks", PlateLevel.Name, modifier = Modifier.weight(1f))
                TextButton(onClick = { onEdit(null) }) { Text("Create") }
            }
            Text(
                if (scope?.startsWith("chat:") == true) "Enabled books provide context for this chat. Persona books are included automatically. Tap a book to edit it."
                else "Enabled books provide context whenever you chat as this persona. Tap a book to edit it.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !favoritesOnly, onClick = { favoritesOnly = false }, label = { Text("All") })
                FilterChip(selected = favoritesOnly, onClick = { favoritesOnly = true }, label = { Text("Favorites") })
            }
            LazyColumn(Modifier.weight(1f, fill = false)) {
                if (books.isEmpty()) item {
                    Text(
                        if (favoritesOnly) "No favorite lorebooks yet. Tap a star to add one."
                        else "No lorebooks yet. Create one here or import JSON in Settings.",
                        modifier = Modifier.padding(20.dp),
                    )
                }
                items(books, key = { it.id }) { book ->
                    ListItem(
                        modifier = Modifier.clickable { onEdit(book.id) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(book.name) },
                        supportingContent = {
                            Text("${book.entryCount} entries" + if (book.inherited) " · From persona" else "")
                        },
                        leadingContent = {
                            IconButton(onClick = {
                                viewModel.toggleFavorite(book.id)
                            }) {
                                Icon(
                                    if (book.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    contentDescription = if (book.favorite) "Remove ${book.name} from favorites" else "Favorite ${book.name}",
                                    tint = if (book.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        trailingContent = {
                            Switch(
                                checked = selection?.contains(book.id) ?: book.selected,
                                onCheckedChange = { toggle(book.id, it) },
                                modifier = Modifier.semantics { contentDescription = "Use ${book.name}" },
                            )
                        },
                    )
                }
            }
        }
    }
}
