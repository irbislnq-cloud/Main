package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.ForkRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.RpChat

private data class BranchTreeNode(
    val chat: RpChat,
    val depth: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BranchTreeSheet(
    activeChat: RpChat,
    allChats: List<RpChat>,
    canCreateBranchHere: Boolean,
    onDismiss: () -> Unit,
    onSelectBranch: (String) -> Unit,
    onCreateBranchFromLatest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val rootId = activeChat.rootChatId.ifBlank { activeChat.id }

    val treeNodes = remember(activeChat.id, allChats) {
        val family = allChats.filter {
            it.id == rootId || it.rootChatId == rootId || it.id == activeChat.id
        }.distinctBy { it.id }

        val byParent = family.groupBy { it.parentChatId }
        val ordered = mutableListOf<BranchTreeNode>()
        val visited = mutableSetOf<String>()

        fun dfs(node: RpChat, depth: Int) {
            if (!visited.add(node.id)) return
            ordered.add(BranchTreeNode(node, depth))
            val children = byParent[node.id].orEmpty().sortedBy { it.branchPointOrder }
            children.forEach { child -> dfs(child, (depth + 1).coerceAtMost(4)) }
        }

        val roots = family.filter { it.parentChatId.isBlank() || it.id == rootId }
            .sortedBy { it.createdAt?.seconds ?: 0L }
        roots.forEach { dfs(it, 0) }
        family.filter { it.id !in visited }.forEach { dfs(it, 1) }
        ordered
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 580.dp)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccountTree,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text("Дерево веток сюжета", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            text = "Связанных линий: ${treeNodes.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (canCreateBranchHere) {
                    Button(
                        onClick = {
                            onDismiss()
                            onCreateBranchFromLatest()
                        },
                        modifier = Modifier.testTag("branch_tree_fork_button")
                    ) {
                        Icon(Icons.Outlined.ForkRight, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ответвить")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val connectorColor = MaterialTheme.colorScheme.outlineVariant
            val activeAccent = MaterialTheme.colorScheme.primary

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                items(treeNodes, key = { it.chat.id }) { item ->
                    val isCurrent = item.chat.id == activeChat.id
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item.depth > 0) {
                            val indentWidth = (item.depth * 18).dp
                            Canvas(
                                modifier = Modifier
                                    .width(indentWidth)
                                    .height(52.dp)
                            ) {
                                val x = size.width - 12.dp.toPx()
                                val midY = size.height / 2f
                                drawLine(
                                    color = if (isCurrent) activeAccent else connectorColor,
                                    start = Offset(x, 0f),
                                    end = Offset(x, midY),
                                    strokeWidth = 2.dp.toPx()
                                )
                                drawLine(
                                    color = if (isCurrent) activeAccent else connectorColor,
                                    start = Offset(x, midY),
                                    end = Offset(size.width, midY),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                        }

                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = if (isCurrent) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                            border = BorderStroke(
                                width = if (isCurrent) 1.5.dp else 1.dp,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    if (!isCurrent) onSelectBranch(item.chat.id)
                                    onDismiss()
                                }
                                .testTag("branch_node_${item.chat.id}")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (item.depth == 0) {
                                                MaterialTheme.colorScheme.tertiaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.secondaryContainer
                                            }
                                        ) {
                                            Text(
                                                text = if (item.depth == 0) "Корень" else item.chat.branchLabel.ifBlank { "Ветка" },
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Text(
                                            text = item.chat.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (isCurrent) {
                                        Text(
                                            text = "Активна",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                if (item.chat.lastMessagePreview.isNotBlank()) {
                                    Text(
                                        text = item.chat.lastMessagePreview,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Text(
                                    text = buildString {
                                        append("Ходов: ${item.chat.messageCount}")
                                        if (item.depth > 0) {
                                            append(" • Форк с хода #${item.chat.branchPointOrder + 1}")
                                        }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
