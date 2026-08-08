package com.fardeenkhan.moodtune.feature.playlist.ui.components

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

/**
 * Long-press-drag-to-reorder for a [LazyColumn][androidx.compose.foundation.lazy.LazyColumn].
 * Tracks the dragged item purely by its stable key ([draggedItemId]) rather than its list index,
 * so it keeps following the same logical item across reorders even as the underlying data (and
 * therefore each item's index) shifts mid-drag.
 *
 * [onDrag] compares the dragged item's current on-screen position against every other *visible*
 * item's bounds; once it has been dragged past another item's midpoint-ish overlap threshold,
 * [onMoveState] is invoked to swap them in the backing data, and the drag's reference point
 * ("initiallyDraggedElement") is rebased to the item it just swapped with so the running
 * [dragdistance] stays relative to the new position instead of compounding drift.
 */
class DragDropListState(
    private val listState: LazyListState,
    private val onMoveState: State<(Int, Int) -> Unit>
) {

    var draggedItemId by mutableStateOf<Any?>(null)
        private set

    val draggedIndex: Int?
        get() = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == draggedItemId }
            ?.index

    var dragdistance by mutableStateOf(0f)
        private set


    private var initiallyDraggedElement by mutableStateOf<LazyListItemInfo?>(null)

     fun onDragStart(offset: Offset){

        listState.layoutInfo.visibleItemsInfo.firstOrNull {
            offset.y.toInt() in it.offset..(it.offset + it.size)
        }?.also {
            draggedItemId = it.key
            initiallyDraggedElement = it
        }
    }



    fun onDrag(delta: Offset){
        dragdistance += delta.y
        
        val currentItemInfo = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == draggedItemId } ?: return
        
        val fromindex = currentItemInfo.index
        val initialOffset = initiallyDraggedElement?.offset ?: return
        val currentTop = initialOffset + dragdistance
        val currentBottom = currentTop + currentItemInfo.size

        listState.layoutInfo.visibleItemsInfo
            .filter {
                it.index != fromindex && it.overlaps(currentTop.toInt(), currentBottom.toInt())
            }
            .firstOrNull { candidates ->
                if (dragdistance > 0) {
                    currentBottom > candidates.offset + candidates.size
                } else {
                    currentTop < candidates.offset
                }
            }?.let { targetItem ->
                onMoveState.value(fromindex, targetItem.index)
                // When we move, we update the "initial" element to the target's info
                // so the relative dragdistance calculation stays correct.
                initiallyDraggedElement = targetItem
                dragdistance = 0f
            }

    }


    fun onDragEnd(){
        draggedItemId = null
        dragdistance = 0f
        initiallyDraggedElement = null
    }

    fun getDraggedOffset(): Float{
        val original = initiallyDraggedElement?.offset ?: return 0f
        val currentItemInfo = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == draggedItemId } ?: return 0f
        return original + dragdistance - currentItemInfo.offset
    }
}

private fun LazyListItemInfo.overlaps(top: Int, bottom: Int): Boolean {
    return offset < bottom && (offset + size) > top
}

@Composable
fun rememberDragDropListState(
    lazyListState: LazyListState = rememberLazyListState(),
    onMove: (Int, Int) -> Unit
): DragDropListState{
    val onMoveState = rememberUpdatedState(onMove)
    return remember {
        DragDropListState(lazyListState, onMoveState)
    }
}