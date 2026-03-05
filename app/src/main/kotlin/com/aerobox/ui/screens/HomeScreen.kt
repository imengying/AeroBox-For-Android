package com.aerobox.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aerobox.R
import com.aerobox.ui.components.ConnectionCard
import com.aerobox.ui.components.NodeListSheet
import com.aerobox.ui.components.TrafficStatsCard
import com.aerobox.utils.showToast
import com.aerobox.viewmodel.HomeViewModel

@Composable
fun HomeScreen(viewModel: HomeViewModel = viewModel()) {
    val context = LocalContext.current
    val vpnState by viewModel.vpnState.collectAsStateWithLifecycle()
    val trafficStats by viewModel.trafficStats.collectAsStateWithLifecycle()
    val selectedNode by viewModel.selectedNode.collectAsStateWithLifecycle()
    val connectionDuration by viewModel.connectionDuration.collectAsStateWithLifecycle()
    val allNodes by viewModel.allNodes.collectAsStateWithLifecycle()
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
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ConnectionCard(
                isConnected = vpnState.isConnected,
                nodeName = selectedNode?.name ?: stringResource(R.string.not_selected),
                nodeAddress = selectedNode?.let { "${it.server}:${it.port}" } ?: "--",
                connectionDuration = connectionDuration,
                onToggleConnection = {
                    val permissionIntent = viewModel.toggleConnection(context)
                    if (permissionIntent != null) {
                        permissionLauncher.launch(permissionIntent)
                    }
                },
                onNodeNameClick = { showNodeList = true }
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
        NodeListSheet(
            nodes = allNodes,
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
}
