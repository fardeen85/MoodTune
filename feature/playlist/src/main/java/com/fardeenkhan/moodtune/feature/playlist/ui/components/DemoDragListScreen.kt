package com.fardeenkhan.moodtune.feature.playlist.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.ExperimentalFoundationApi

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DragListScreen(){
    val items = remember {
        mutableStateListOf(
            Item(1,"Item 1"),
            Item(2,"Item 2"),
            Item(3,"Item 3"),
            Item(4,"Item 4"),
            Item(5,"Item 5"),

        )
    }

    val listState = rememberLazyListState()
    val dragdropState = rememberDragDropListState(listState,
        onMove = { from, to ->

            items.apply {
                add(to,removeAt(from))
            }
        })


    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit){
                detectDragGesturesAfterLongPress(
                    onDragStart = {dragdropState.onDragStart(it)},
                    onDragEnd = {
                        dragdropState.onDragEnd()
                    },
                    onDragCancel = {
                        dragdropState.onDragEnd()
                    },
                    onDrag = { _, dragAmount ->
                        dragdropState.onDrag(dragAmount)
                    }
                )
            }
    ) {

        itemsIndexed(items, key = {_,item,->item.id}){
            index, item ->
           val isDragging = dragdropState.draggedIndex == index

            val animatedOffset by animateFloatAsState(
                targetValue = if (isDragging) dragdropState.getDraggedOffset() else 0f,
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    dampingRatio = Spring.DampingRatioLowBouncy
                ),
                label = "dragOffset"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer{
                        translationY = animatedOffset
                        shadowElevation=if(isDragging) 8f else 0f
                        scaleX = if(isDragging) 1.05f else 1f
                        scaleY = if(isDragging) 1.05f else 1f
                    }
                    .background(
                        if(isDragging) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.medium,
                    )
                    .padding(16.dp),

            ) {
                Text(item.name)
            }
        }

    }


}


@Composable
@Preview(showBackground = true,device = "spec:width=411dp,height=891dp")
fun previewDragDropList(){
    DragListScreen()
}