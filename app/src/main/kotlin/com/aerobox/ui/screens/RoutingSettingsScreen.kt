package com.aerobox.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aerobox.core.geo.GeoAssetManager
import com.aerobox.ui.components.AppSnackbarHost
import com.aerobox.ui.components.SectionHeader
import com.aerobox.ui.components.SettingItem
import com.aerobox.ui.icons.AppIcons
import com.aerobox.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutingSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(
        viewModelStoreOwner = LocalContext.current as androidx.activity.ComponentActivity
    )
) {
    val enableGeoRules by viewModel.enableGeoRules.collectAsStateWithLifecycle()
    val enableGeoCnDomainRule by viewModel.enableGeoCnDomainRule.collectAsStateWithLifecycle()
    val enableGeoCnIpRule by viewModel.enableGeoCnIpRule.collectAsStateWithLifecycle()
    val enableGeoAdsBlock by viewModel.enableGeoAdsBlock.collectAsStateWithLifecycle()
    val enableGeoBlockQuic by viewModel.enableGeoBlockQuic.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var geoUpdating by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("路由") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { AppSnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { SectionHeader(title = "规则") }
            item {
                SettingItem(
                    icon = { Icon(AppIcons.Security, contentDescription = null) },
                    title = "规则",
                    supporting = if (enableGeoRules) {
                        "已开启，可按需启用下方规则"
                    } else {
                        "默认关闭，按需开启"
                    },
                    trailing = {
                        Switch(
                            checked = enableGeoRules,
                            onCheckedChange = { scope.launch { viewModel.setEnableGeoRules(it) } }
                        )
                    }
                )
            }
            if (enableGeoRules) {
                item {
                    SettingItem(
                        icon = { Icon(AppIcons.Security, contentDescription = null) },
                        title = "屏蔽 QUIC",
                        supporting = "network: udp + port: 443",
                        trailing = {
                            Switch(
                                checked = enableGeoBlockQuic,
                                onCheckedChange = { scope.launch { viewModel.setEnableGeoBlockQuic(it) } }
                            )
                        }
                    )
                }
                item {
                    SettingItem(
                        icon = { Icon(AppIcons.Security, contentDescription = null) },
                        title = "中国域名规则",
                        supporting = "rule_set: geosite-cn",
                        trailing = {
                            Switch(
                                checked = enableGeoCnDomainRule,
                                onCheckedChange = { scope.launch { viewModel.setEnableGeoCnDomainRule(it) } }
                            )
                        }
                    )
                }
                item {
                    SettingItem(
                        icon = { Icon(AppIcons.Security, contentDescription = null) },
                        title = "中国 IP 规则",
                        supporting = "rule_set: geoip-cn",
                        trailing = {
                            Switch(
                                checked = enableGeoCnIpRule,
                                onCheckedChange = { scope.launch { viewModel.setEnableGeoCnIpRule(it) } }
                            )
                        }
                    )
                }
                item {
                    SettingItem(
                        icon = { Icon(AppIcons.Security, contentDescription = null) },
                        title = "屏蔽广告",
                        supporting = "rule_set: geosite-category-ads-all",
                        trailing = {
                            Switch(
                                checked = enableGeoAdsBlock,
                                onCheckedChange = { scope.launch { viewModel.setEnableGeoAdsBlock(it) } }
                            )
                        }
                    )
                }
            }

            item { SectionHeader(title = "资源") }
            item {
                val hasFiles = GeoAssetManager.hasLocalFiles(context)
                val geoIpSize = GeoAssetManager.getGeoIpSize(context)
                val geoSiteSize = GeoAssetManager.getGeoSiteSize(context)
                val geoAdsSize = GeoAssetManager.getGeoAdsSize(context)
                SettingItem(
                    onClick = {
                        if (!geoUpdating) {
                            geoUpdating = true
                            scope.launch {
                                val ok = GeoAssetManager.updateAll(context)
                                geoUpdating = false
                                snackbarHostState.showSnackbar(
                                    if (ok) "路由资源更新完成" else "路由资源更新失败，请检查网络"
                                )
                            }
                        }
                    },
                    icon = { Icon(AppIcons.Security, contentDescription = null) },
                    title = if (hasFiles) "更新路由资源" else "下载路由资源",
                    supporting = if (hasFiles) {
                        "中国 IP: $geoIpSize · 中国域名: $geoSiteSize · 广告: $geoAdsSize（官方源）"
                    } else {
                        "仅使用官方源 SagerNet"
                    },
                    trailing = {
                        if (geoUpdating) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(4.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                        }
                    }
                )
            }
        }
    }
}
