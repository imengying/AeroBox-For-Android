package com.aerobox.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aerobox.R
import com.aerobox.data.model.RoutingMode
import com.aerobox.ui.components.ConnectionCard
import com.aerobox.ui.components.NodeListSheet
import com.aerobox.ui.components.TrafficStatsCard
import com.aerobox.utils.showToast
import com.aerobox.viewmodel.ConnectionFixAction
import com.aerobox.viewmodel.HomeViewModel

@Composable
fun HomeScreen(viewModel: HomeViewModel = viewModel()) {
    val context = LocalContext.current
    val vpnState by viewModel.vpnState.collectAsStateWithLifecycle()
    val trafficStats by viewModel.trafficStats.collectAsStateWithLifecycle()
    val selectedNode by viewModel.selectedNode.collectAsStateWithLifecycle()
    val connectionDuration by viewModel.connectionDuration.collectAsStateWithLifecycle()
    val allNodes by viewModel.allNodes.collectAsStateWithLifecycle()
    val routingMode by viewModel.routingMode.collectAsStateWithLifecycle()
    val detectedIp by viewModel.detectedIp.collectAsStateWithLifecycle()
    val connectionIssue by viewModel.connectionIssue.collectAsStateWithLifecycle()
    var showNodeList by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onVpnPermissionGranted(context)
        } else {
            context.showToast(context.getString(R.string.permission_required))
        }
    }

    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 64.dp, bottom = 24.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ConnectionCard(
                isConnected = vpnState.isConnected,
                nodeName = selectedNode?.name ?: stringResource(R.string.not_selected),
                nodeAddress = selectedNode?.type?.name ?: "--",
                connectionDuration = connectionDuration,
                onToggleConnection = {
                    val permissionIntent = viewModel.toggleConnection(context)
                    if (permissionIntent != null) {
                        permissionLauncher.launch(permissionIntent)
                    }
                },
                onNodeNameClick = { showNodeList = true },
                onTestNetwork = {
                    if (selectedNode != null) {
                        viewModel.testSingleNodeLatency(selectedNode!!)
                    }
                    viewModel.refreshNetworkInfo()
                }
            )
        }

        item {
            NetworkDetectCard(
                ip = detectedIp,
                onClick = { viewModel.refreshNetworkInfo() }
            )
        }

        // ── Routing mode segmented bar ──
        item {
            RoutingModeBar(
                selected = routingMode,
                onSelect = { viewModel.setRoutingMode(it) }
            )
        }

        item {
            TrafficStatsCard(stats = trafficStats)
        }

        if (selectedNode == null) {
            item {
                Text(
                    text = stringResource(R.string.hint_add_subscription),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Node list bottom sheet
    if (showNodeList) {
        val subscriptionNames by viewModel.subscriptionNames.collectAsStateWithLifecycle()
        NodeListSheet(
            nodes = allNodes,
            subscriptionNames = subscriptionNames,
            selectedNodeId = selectedNode?.id ?: -1,
            onNodeSelected = { node ->
                viewModel.selectNode(node)
                showNodeList = false
            },
            onTestAll = { viewModel.testAllNodesLatency() },
            onTestNode = { node -> viewModel.testSingleNodeLatency(node) },
            onDismiss = { showNodeList = false }
        )
    }

    connectionIssue?.let { issue ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissConnectionIssue() },
            title = { Text(issue.title) },
            text = {
                Text(
                    buildString {
                        append(issue.message)
                        if (issue.rawError.isNotBlank()) {
                            append("\n\n原始错误：")
                            append(issue.rawError.take(220))
                        }
                    }
                )
            },
            confirmButton = {
                val action = issue.fixAction
                if (action != null) {
                    TextButton(
                        onClick = { viewModel.applyConnectionFix(context, action) }
                    ) {
                        Text(action.label)
                    }
                } else {
                    TextButton(onClick = { viewModel.dismissConnectionIssue() }) {
                        Text("知道了")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissConnectionIssue() }) {
                    Text(
                        if (issue.fixAction == ConnectionFixAction.REFRESH_SUBSCRIPTIONS) {
                            "稍后手动处理"
                        } else {
                            "取消"
                        }
                    )
                }
            }
        )
    }
}

@Composable
private fun NetworkDetectCard(
    ip: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "网络检测",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = ip,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
        )
    }
}

/**
 * Rounded segmented bar for routing mode (规则 / 全局 / 直连).
 */
@Composable
private fun RoutingModeBar(
    selected: RoutingMode,
    onSelect: (RoutingMode) -> Unit
) {
    // Show the currently supported 3 routing modes.
    val modes = listOf(
        RoutingMode.RULE_BASED to "规则",
        RoutingMode.GLOBAL_PROXY to "全局",
        RoutingMode.DIRECT to "直连"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        modes.forEach { (mode, label) ->
            val isSelected = selected == mode
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest,
                label = "segBg"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "segText"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bgColor)
                    .clickable { onSelect(mode) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = textColor,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
