package com.fardeenkhan.moodtune.feature.playlist.ui.components

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState

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


    private fun LazyListState.findIteminfo(index: Int): LazyListItemInfo?{
        val draggedItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        return if (draggedItem != null) draggedItem else null
    }
}

private fun LazyListItemInfo.overlaps(top:Int,bottom: Int): Boolean
{
    return offset< bottom && (offset + size)> top


}

data class Item(
    val id: Int,
    val name: String
)

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