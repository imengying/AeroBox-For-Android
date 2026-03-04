package com.aerobox.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aerobox.R
import com.aerobox.data.model.ProxyNode
import com.aerobox.data.model.ProxyType
import com.aerobox.data.model.Subscription
import com.aerobox.ui.theme.SingBoxVPNTheme
import com.aerobox.utils.getLatencyColor
import com.aerobox.viewmodel.ProfilesViewModel

@Composable
fun ProfilesScreen(viewModel: ProfilesViewModel = viewModel()) {
    val context = LocalContext.current
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var deletingSubscription by remember { mutableStateOf<Subscription?>(null) }
    val expandedStates = remember { mutableStateMapOf<Long, Boolean>() }

    LaunchedEffect(error) {
        if (error != null) {
            val message = when (error) {
                "invalid_url" -> context.getString(R.string.invalid_url)
                else -> context.getString(R.string.operation_failed)
            }
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null
                    )
                },
                text = {
                    Text(text = stringResource(R.string.add_subscription))
                }
            )
        }
    ) { innerPadding ->
        if (subscriptions.isEmpty()) {
            EmptySubscriptionState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                onAddClick = { showAddDialog = true }
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items = subscriptions, key = { it.id }) { subscription ->
                    val nodes by viewModel.nodesFlow(subscription.id)
                        .collectAsStateWithLifecycle(initialValue = emptyList())
                    val expanded = expandedStates[subscription.id] ?: false

                    SubscriptionCard(
                        subscription = subscription,
                        nodes = nodes,
                        expanded = expanded,
                        isLoading = isLoading,
                        onToggleExpand = {
                            expandedStates[subscription.id] = !expanded
                        },
                        onRefresh = { viewModel.updateSubscription(subscription) },
                        onDelete = { deletingSubscription = subscription },
                        onTestAllLatency = { viewModel.testAllLatencies(subscription.id) },
                        onNodeClick = { node -> viewModel.selectNode(node) },
                        onTestNodeLatency = { node -> viewModel.testLatency(node) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddSubscriptionDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, url ->
                viewModel.addSubscription(name, url)
                showAddDialog = false
            }
        )
    }

    deletingSubscription?.let { subscription ->
        AlertDialog(
            onDismissRequest = { deletingSubscription = null },
            title = { Text(stringResource(R.string.delete_subscription)) },
            text = { Text(subscription.name) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSubscription(subscription)
                        deletingSubscription = null
                    }
                ) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingSubscription = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun EmptySubscriptionState(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Text(
            text = stringResource(R.string.no_subscription),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = stringResource(R.string.hint_add_subscription),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        Button(
            onClick = onAddClick,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(stringResource(R.string.add_subscription))
        }
    }
}

@Composable
private fun SubscriptionCard(
    subscription: Subscription,
    nodes: List<ProxyNode>,
    expanded: Boolean,
    isLoading: Boolean,
    onToggleExpand: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onTestAllLatency: () -> Unit,
    onNodeClick: (ProxyNode) -> Unit,
    onTestNodeLatency: (ProxyNode) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = subscription.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            supportingContent = {
                Text(text = "${subscription.nodeCount} 节点")
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onTestAllLatency, enabled = !isLoading) {
                        Icon(
                            imageVector = Icons.Filled.Speed,
                            contentDescription = stringResource(R.string.latency_test)
                        )
                    }
                    IconButton(onClick = onRefresh, enabled = !isLoading) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.refresh)
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.delete_subscription),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    IconButton(onClick = onToggleExpand) {
                        Icon(
                            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null
                        )
                    }
                }
            }
        )

        AnimatedVisibility(visible = expanded) {
            Column {
                HorizontalDivider()
                nodes.forEach { node ->
                    NodeListItem(
                        node = node,
                        onClick = { onNodeClick(node) },
                        onTestLatency = { onTestNodeLatency(node) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun NodeListItem(
    node: ProxyNode,
    onClick: () -> Unit,
    onTestLatency: () -> Unit
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        headlineContent = {
            Text(
                text = node.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text("${node.type.name} • ${node.server}:${node.port}")
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = node.latency.getLatencyColor(),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = if (node.latency >= 0) "${node.latency}ms" else "--",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                IconButton(onClick = onTestLatency) {
                    Icon(imageVector = Icons.Filled.Speed, contentDescription = null)
                }
            }
        }
    )
}

@Composable
private fun AddSubscriptionDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(imageVector = Icons.Filled.Add, contentDescription = null)
        },
        title = {
            Text(stringResource(R.string.add_subscription))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.subscription_name)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.subscription_url)) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = { onConfirm(name, url) },
                enabled = name.isNotBlank() && url.isNotBlank()
            ) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun ProfilesScreenPreview() {
    SingBoxVPNTheme {
        SubscriptionCard(
            subscription = Subscription(id = 1, name = "示例订阅", url = "https://example.com", nodeCount = 2),
            nodes = listOf(
                ProxyNode(name = "节点 A", type = ProxyType.VLESS, server = "1.1.1.1", port = 443, latency = 82),
                ProxyNode(name = "节点 B", type = ProxyType.TROJAN, server = "8.8.8.8", port = 443, latency = 256)
            ),
            expanded = true,
            isLoading = false,
            onToggleExpand = {},
            onRefresh = {},
            onDelete = {},
            onTestAllLatency = {},
            onNodeClick = {},
            onTestNodeLatency = {}
        )
    }
}
