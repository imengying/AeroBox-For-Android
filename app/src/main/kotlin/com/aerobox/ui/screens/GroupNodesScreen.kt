package com.aerobox.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aerobox.R
import com.aerobox.data.model.ProxyNode
import com.aerobox.data.model.isLocalGroup
import com.aerobox.data.repository.SubscriptionRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupNodesScreen(
    subscriptionId: Long,
    onNavigateBack: () -> Unit
) {
    val isUngrouped = subscriptionId == 0L
    val context = LocalContext.current
    val repository = remember(context) { SubscriptionRepository(context) }
    val subscription by repository.observeSubscriptionById(subscriptionId)
        .collectAsStateWithLifecycle(initialValue = null)
    val nodes by repository.observeNodesInGroup(subscriptionId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    var deleteNodeTarget by remember { mutableStateOf<ProxyNode?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteGroupDialog by remember { mutableStateOf(false) }

    // If the subscription row disappears (deleted elsewhere, or navigated here
    // with a stale id), bail back to the subscription list so the user isn't
    // stranded on an empty screen.  Skip this check for the virtual ungrouped
    // bucket (subscriptionId = 0) which has no backing Subscription row.
    var everLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(subscription) {
        if (isUngrouped) {
            everLoaded = true
        } else if (subscription != null) {
            everLoaded = true
        } else if (everLoaded) {
            onNavigateBack()
        }
    }

    val isLocal = isUngrouped || subscription?.isLocalGroup() == true
    val ungroupedLabel = stringResource(R.string.group_ungrouped)
    val groupName = if (isUngrouped) ungroupedLabel else subscription?.name.orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = groupName.ifBlank { stringResource(R.string.edit_group) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (isLocal) {
                        IconButton(onClick = { showRenameDialog = true }) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.rename_group)
                            )
                        }
                        IconButton(onClick = { showDeleteGroupDialog = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.delete_group),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        if (nodes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.group_empty_nodes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(nodes, key = { it.id }) { node ->
                    NodeRow(
                        node = node,
                        onDelete = { deleteNodeTarget = node }
                    )
                }
            }
        }
    }

    deleteNodeTarget?.let { node ->
        AlertDialog(
            onDismissRequest = { deleteNodeTarget = null },
            title = { Text(stringResource(R.string.delete_node)) },
            text = {
                Text(stringResource(R.string.delete_node_confirm, node.name.ifBlank { node.server }))
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.deleteNode(node.id) }
                    deleteNodeTarget = null
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteNodeTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showRenameDialog) {
        val currentSubscription = subscription
        if (currentSubscription != null && currentSubscription.isLocalGroup()) {
            RenameGroupDialog(
                initialName = currentSubscription.name,
                onDismiss = { showRenameDialog = false },
                onConfirm = { newName ->
                    scope.launch {
                        repository.renameLocalGroup(currentSubscription, newName)
                    }
                    showRenameDialog = false
                }
            )
        } else {
            showRenameDialog = false
        }
    }

    if (showDeleteGroupDialog) {
        val currentSubscription = subscription
        AlertDialog(
            onDismissRequest = { showDeleteGroupDialog = false },
            title = { Text(stringResource(R.string.delete_group)) },
            text = {
                Text(
                    stringResource(
                        R.string.delete_local_group_confirm,
                        currentSubscription?.name.orEmpty()
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val sub = currentSubscription
                    showDeleteGroupDialog = false
                    if (sub != null) {
                        scope.launch {
                            repository.deleteSubscription(sub)
                            onNavigateBack()
                        }
                    }
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteGroupDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun NodeRow(
    node: ProxyNode,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.name.ifBlank { "${node.server}:${node.port}" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${node.type.displayName()} · ${node.server}:${node.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.delete_node),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun RenameGroupDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_group)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.group_new_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
