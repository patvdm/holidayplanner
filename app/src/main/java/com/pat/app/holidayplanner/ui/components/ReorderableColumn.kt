package com.pat.app.holidayplanner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pat.app.holidayplanner.R
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * A list whose rows can be dragged into a new order.
 *
 * The drag moves a local copy so the list follows the finger, and only when the finger lifts does
 * one row get a new sort key, computed from the neighbours it ended up between. That is the whole
 * point of fractional keys: reordering writes a single row, not the whole list.
 *
 * [itemContent] receives a handle to place wherever the row should be grabbed. When [reorderable]
 * is false no handle is drawn and the list behaves like an ordinary one. [footer] is pinned after
 * the last row, inside the scroll, for actions that belong with the list rather than over it.
 * [groupOf] splits the list into groups a row cannot be dragged out of, high priority above the
 * rest say; its new neighbours are then taken from its own group only.
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    idOf: (T) -> String,
    sortKeyOf: (T) -> String,
    reorderable: Boolean,
    onMoved: (id: String, previousKey: String?, nextKey: String?) -> Unit,
    modifier: Modifier = Modifier,
    groupOf: (T) -> Any? = { null },
    footer: (@Composable () -> Unit)? = null,
    itemContent: @Composable (item: T, handle: (@Composable () -> Unit)?) -> Unit,
) {
    var ordered by remember { mutableStateOf(items) }
    var draggedId by remember { mutableStateOf<String?>(null) }

    // Follow the database unless a drag is in progress, which would fight the finger.
    LaunchedEffect(items) {
        if (draggedId == null) ordered = items
    }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = ordered.indexOfFirst { idOf(it) == from.key }
        val toIndex = ordered.indexOfFirst { idOf(it) == to.key }
        if (fromIndex >= 0 && toIndex >= 0 && groupOf(ordered[fromIndex]) == groupOf(ordered[toIndex])) {
            ordered = ordered.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            draggedId = idOf(ordered[toIndex])
        }
    }

    LazyColumn(state = listState, modifier = modifier) {
        items(ordered, key = idOf) { item ->
            ReorderableItem(reorderState, key = idOf(item)) {
                val handle: (@Composable () -> Unit)? = if (!reorderable) {
                    null
                } else {
                    {
                        Icon(
                            imageVector = Icons.Filled.DragHandle,
                            contentDescription = stringResource(R.string.action_reorder),
                            modifier = Modifier.draggableHandle(
                                onDragStopped = {
                                    val moved = draggedId
                                    draggedId = null
                                    if (moved != null) {
                                        val group = ordered.firstOrNull { idOf(it) == moved }?.let(groupOf)
                                        val own = ordered.filter { groupOf(it) == group }
                                        persist(own, moved, idOf, sortKeyOf, onMoved)
                                    }
                                },
                            ),
                        )
                    }
                }
                Surface(tonalElevation = if (draggedId == idOf(item)) 4.dp else 0.dp) {
                    Box { itemContent(item, handle) }
                }
            }
        }
        if (footer != null) item(key = "footer") { footer() }
    }
}

/** Turns the position the row was dropped at into a sort key between its two new neighbours. */
private fun <T> persist(
    ordered: List<T>,
    movedId: String,
    idOf: (T) -> String,
    sortKeyOf: (T) -> String,
    onMoved: (String, String?, String?) -> Unit,
) {
    val index = ordered.indexOfFirst { idOf(it) == movedId }
    if (index < 0) return
    onMoved(
        movedId,
        ordered.getOrNull(index - 1)?.let(sortKeyOf),
        ordered.getOrNull(index + 1)?.let(sortKeyOf),
    )
}

/** Shown instead of a list when there is nothing in it yet. */
@Composable
fun EmptyListMessage(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(24.dp),
    )
}

/**
 * An empty-state message that still answers a pull-down gesture.
 *
 * A plain centred message is not scrollable, so pull to refresh would do nothing on exactly the
 * screen where someone is most likely to try it: the one with nothing on it yet.
 */
@Composable
fun PullableEmptyState(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        content()
    }
}
