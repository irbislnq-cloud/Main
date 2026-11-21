package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.WorldLocationNode
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LocationGraphCanvas(
    locations: List<WorldLocationNode>,
    currentLocationId: String,
    focusedLocationId: String? = null,
    onUpdateNodes: (List<WorldLocationNode>) -> Unit,
    onMoveToLocation: (String) -> Unit,
    onGenerateLocationBackdrop: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedNodeId by remember(locations, currentLocationId, focusedLocationId) {
        mutableStateOf(
            focusedLocationId?.takeIf { fid -> locations.any { it.id == fid } }
                ?: currentLocationId.takeIf { cid -> locations.any { it.id == cid } }
                ?: locations.firstOrNull()?.id
        )
    }
    LaunchedEffect(focusedLocationId) {
        if (!focusedLocationId.isNullOrBlank() && locations.any { it.id == focusedLocationId }) {
            selectedNodeId = focusedLocationId
        }
    }

    var canvasSize by remember { mutableStateOf(IntSize(800, 500)) }
    var draggingNodeId by remember { mutableStateOf<String?>(null) }

    var newLocName by remember { mutableStateOf("") }
    var newLocDesc by remember { mutableStateOf("") }
    var newLocDanger by remember { mutableStateOf("") }

    val textMeasurer = rememberTextMeasurer()

    // Ensure normalized coordinates (0.12..0.88) for any node without saved posX/posY
    val resolvedPositions = remember(locations) {
        val total = locations.size.coerceAtLeast(1)
        locations.mapIndexed { idx, node ->
            val x = if (node.posX in 0.05..0.95) {
                node.posX.toFloat()
            } else {
                val angle = (2.0 * PI * idx / total) - PI / 2.0
                (0.5 + 0.32 * cos(angle)).toFloat().coerceIn(0.12f, 0.88f)
            }
            val y = if (node.posY in 0.05..0.95) {
                node.posY.toFloat()
            } else {
                val angle = (2.0 * PI * idx / total) - PI / 2.0
                (0.5 + 0.30 * sin(angle)).toFloat().coerceIn(0.14f, 0.86f)
            }
            node.id to Offset(x, y)
        }.toMap()
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val surfaceLineColor = MaterialTheme.colorScheme.outlineVariant
    val visitedColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (locations.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Explore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "Карта мира пока пуста",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Добавьте первую точку ниже или нажмите на любую локацию в тексте чата — она появится здесь.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { canvasSize = it }
                            .pointerInput(locations, resolvedPositions) {
                                detectTapGestures { tapOffset ->
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    val h = size.height.toFloat().coerceAtLeast(1f)
                                    val hit = locations.firstOrNull { node ->
                                        val norm = resolvedPositions[node.id] ?: return@firstOrNull false
                                        val cx = norm.x * w
                                        val cy = norm.y * h
                                        hypot(tapOffset.x - cx, tapOffset.y - cy) <= 64f
                                    }
                                    if (hit != null) {
                                        selectedNodeId = hit.id
                                    }
                                }
                            }
                            .pointerInput(locations, resolvedPositions) {
                                detectDragGestures(
                                    onDragStart = { startOffset ->
                                        val w = size.width.toFloat().coerceAtLeast(1f)
                                        val h = size.height.toFloat().coerceAtLeast(1f)
                                        val hit = locations.firstOrNull { node ->
                                            val norm = resolvedPositions[node.id] ?: return@firstOrNull false
                                            hypot(startOffset.x - norm.x * w, startOffset.y - norm.y * h) <= 72f
                                        }
                                        draggingNodeId = hit?.id
                                        if (hit != null) selectedNodeId = hit.id
                                    },
                                    onDragEnd = { draggingNodeId = null },
                                    onDragCancel = { draggingNodeId = null },
                                    onDrag = { change, dragAmount ->
                                        val dragId = draggingNodeId ?: return@detectDragGestures
                                        change.consume()
                                        val w = size.width.toFloat().coerceAtLeast(1f)
                                        val h = size.height.toFloat().coerceAtLeast(1f)
                                        val curNorm = resolvedPositions[dragId] ?: Offset(0.5f, 0.5f)
                                        val nextX = ((curNorm.x * w + dragAmount.x) / w).coerceIn(0.08f, 0.92f)
                                        val nextY = ((curNorm.y * h + dragAmount.y) / h).coerceIn(0.10f, 0.90f)
                                        onUpdateNodes(
                                            locations.map { loc ->
                                                if (loc.id == dragId) {
                                                    loc.copy(posX = nextX.toDouble(), posY = nextY.toDouble())
                                                } else loc
                                            }
                                        )
                                    }
                                )
                            }
                            .testTag("location_graph_canvas")
                    ) {
                        val w = size.width
                        val h = size.height

                        // Subtle coordinate grid
                        val stepX = w / 6f
                        val stepY = h / 4f
                        for (gx in 1 until 6) {
                            drawLine(
                                color = surfaceLineColor.copy(alpha = 0.25f),
                                start = Offset(stepX * gx, 0f),
                                end = Offset(stepX * gx, h),
                                strokeWidth = 1f
                            )
                        }
                        for (gy in 1 until 4) {
                            drawLine(
                                color = surfaceLineColor.copy(alpha = 0.25f),
                                start = Offset(0f, stepY * gy),
                                end = Offset(w, stepY * gy),
                                strokeWidth = 1f
                            )
                        }

                        // Connection edges
                        val drawnPairs = mutableSetOf<String>()
                        locations.forEach { from ->
                            val fromPos = resolvedPositions[from.id] ?: return@forEach
                            from.connections.forEach { targetId ->
                                val target = locations.firstOrNull { it.id == targetId } ?: return@forEach
                                val pairKey = listOf(from.id, target.id).sorted().joinToString("::")
                                if (!drawnPairs.add(pairKey)) return@forEach
                                val toPos = resolvedPositions[target.id] ?: return@forEach
                                val isActiveEdge = from.id == currentLocationId || target.id == currentLocationId
                                drawLine(
                                    color = if (isActiveEdge) primaryColor.copy(alpha = 0.7f) else surfaceLineColor,
                                    start = Offset(fromPos.x * w, fromPos.y * h),
                                    end = Offset(toPos.x * w, toPos.y * h),
                                    strokeWidth = if (isActiveEdge) 4f else 2.5f,
                                    pathEffect = if (isActiveEdge) null else PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                                )
                            }
                        }

                        // Location nodes
                        locations.forEach { node ->
                            val norm = resolvedPositions[node.id] ?: return@forEach
                            val center = Offset(norm.x * w, norm.y * h)
                            val isCurrent = node.id == currentLocationId
                            val isSelected = node.id == selectedNodeId

                            if (isSelected) {
                                drawCircle(
                                    color = secondaryColor.copy(alpha = 0.30f),
                                    radius = 30f,
                                    center = center
                                )
                            }
                            if (isCurrent) {
                                drawCircle(
                                    color = primaryColor.copy(alpha = 0.28f),
                                    radius = 25f,
                                    center = center
                                )
                            }
                            drawCircle(
                                color = when {
                                    isCurrent -> primaryColor
                                    node.visited -> tertiaryColor
                                    else -> visitedColor
                                },
                                radius = if (isCurrent) 17f else 14f,
                                center = center
                            )
                            if (isSelected) {
                                drawCircle(
                                    color = secondaryColor,
                                    radius = 21f,
                                    center = center,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                                )
                            }

                            val shortLabel = node.name.take(18)
                            val measured = textMeasurer.measure(
                                text = shortLabel,
                                style = TextStyle(
                                    color = onSurfaceColor,
                                    fontSize = 11.sp,
                                    fontWeight = if (isCurrent || isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            )
                            val labelX = (center.x - measured.size.width / 2f)
                                .coerceIn(8f, (w - measured.size.width - 8f).coerceAtLeast(8f))
                            val labelY = (center.y + 22f)
                                .coerceIn(8f, (h - measured.size.height - 6f).coerceAtLeast(8f))
                            drawText(
                                textLayoutResult = measured,
                                topLeft = Offset(labelX, labelY)
                            )
                        }
                    }
                }

                Text(
                    text = "Перетаскивайте узлы пальцем • Нажмите для деталей",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                )
            }
        }

        // Selected location detail card
        val selected = locations.firstOrNull { it.id == selectedNodeId }
        if (selected != null) {
            val isHere = selected.id == currentLocationId
            Surface(
                shape = MaterialTheme.shapes.large,
                color = if (isHere) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("selected_location_card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selected.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            val meta = buildList {
                                if (isHere) add("Вы здесь")
                                else if (selected.visited) add("Посещено")
                                else add("Неисследовано")
                                if (selected.dangerLevel.isNotBlank()) add("Угроза: ${selected.dangerLevel}")
                            }.joinToString(" • ")
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = {
                            val remaining = locations.filterNot { it.id == selected.id }.map { loc ->
                                loc.copy(connections = loc.connections.filterNot { it == selected.id })
                            }
                            onUpdateNodes(remaining)
                            selectedNodeId = remaining.firstOrNull()?.id
                        }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Удалить локацию")
                        }
                    }

                    if (selected.backdropUri.isNotBlank()) {
                        AsyncImage(
                            model = selected.backdropUri,
                            contentDescription = "Фон локации ${selected.name}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }

                    var editDesc by remember(selected.id, selected.description) {
                        mutableStateOf(selected.description)
                    }
                    OutlinedTextField(
                        value = editDesc,
                        onValueChange = {
                            editDesc = it
                            onUpdateNodes(
                                locations.map { loc ->
                                    if (loc.id == selected.id) loc.copy(description = it) else loc
                                }
                            )
                        },
                        label = { Text("Описание локации") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (locations.size > 1) {
                        Text("Связанные переходы:", style = MaterialTheme.typography.labelMedium)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            locations.filter { it.id != selected.id }.forEach { other ->
                                val connected = other.id in selected.connections
                                FilterChip(
                                    selected = connected,
                                    onClick = {
                                        val updated = locations.map { loc ->
                                            when (loc.id) {
                                                selected.id -> loc.copy(
                                                    connections = if (connected) {
                                                        loc.connections - other.id
                                                    } else {
                                                        (loc.connections + other.id).distinct().take(10)
                                                    }
                                                )
                                                other.id -> loc.copy(
                                                    connections = if (connected) {
                                                        loc.connections - selected.id
                                                    } else {
                                                        (loc.connections + selected.id).distinct().take(10)
                                                    }
                                                )
                                                else -> loc
                                            }
                                        }
                                        onUpdateNodes(updated)
                                    },
                                    label = { Text(other.name) }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onGenerateLocationBackdrop(selected.id) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("gen_location_backdrop_btn")
                        ) {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Арт локации")
                        }
                        if (!isHere) {
                            Button(
                                onClick = { onMoveToLocation(selected.id) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("move_to_loc_${selected.id}")
                            ) {
                                Text("Перейти сюда")
                            }
                        }
                    }
                }
            }
        }

        // Quick Add Location form
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Добавить новую локацию", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = newLocName,
                    onValueChange = { newLocName = it },
                    label = { Text("Название (например: Поле светлых)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newLocDesc,
                        onValueChange = { newLocDesc = it },
                        label = { Text("Описание") },
                        singleLine = true,
                        modifier = Modifier.weight(1.4f)
                    )
                    OutlinedTextField(
                        value = newLocDanger,
                        onValueChange = { newLocDanger = it },
                        label = { Text("Угроза") },
                        placeholder = { Text("Средняя") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Button(
                    onClick = {
                        val clean = newLocName.trim().take(80)
                        if (clean.isNotEmpty() && locations.size < 24) {
                            val newId = "loc_${UUID.randomUUID().toString().take(8)}"
                            val anchorId = selectedNodeId ?: currentLocationId.ifBlank { locations.firstOrNull()?.id.orEmpty() }
                            val updated = locations.map { loc ->
                                if (anchorId.isNotBlank() && loc.id == anchorId) {
                                    loc.copy(connections = (loc.connections + newId).distinct().take(10))
                                } else loc
                            } + WorldLocationNode(
                                id = newId,
                                name = clean,
                                description = newLocDesc.trim().take(300),
                                dangerLevel = newLocDanger.trim().take(40),
                                connections = if (anchorId.isNotBlank()) listOf(anchorId) else emptyList(),
                                visited = locations.isEmpty()
                            )
                            onUpdateNodes(updated)
                            selectedNodeId = newId
                            newLocName = ""
                            newLocDesc = ""
                            newLocDanger = ""
                        }
                    },
                    enabled = newLocName.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_location_node_button")
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Добавить на граф карты")
                }
            }
        }
    }
}
