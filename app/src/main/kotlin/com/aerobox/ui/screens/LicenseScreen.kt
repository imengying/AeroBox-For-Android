package com.aerobox.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.aerobox.R
import com.aerobox.ui.components.AppSnackbarHost
import com.aerobox.ui.components.SectionHeader
import com.aerobox.ui.components.SettingItem
import com.aerobox.ui.icons.AppIcons
import com.google.android.gms.oss.licenses.v2.OssLicensesMenuActivity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var singBoxNotice by remember { mutableStateOf("") }
    var showSingBoxNotice by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        singBoxNotice = runCatching {
            context.resources.openRawResource(R.raw.sing_box_notice)
                .bufferedReader()
                .use { it.readText().trim() }
        }.getOrElse { "读取 sing-box 许可证失败" }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.open_source_licenses)) },
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
            item { SectionHeader(title = "核心组件") }
            item {
                SettingItem(
                    onClick = { showSingBoxNotice = true },
                    icon = { Icon(AppIcons.Security, contentDescription = null) },
                    title = "sing-box / libbox",
                    supporting = "SagerNet · GPL-3.0-or-later",
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null
                        )
                    }
                )
            }
            item { SectionHeader(title = "其他依赖") }
            item {
                SettingItem(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(context, OssLicensesMenuActivity::class.java)
                            )
                        }.onFailure {
                            scope.launch {
                                snackbarHostState.showSnackbar("打开许可证页面失败")
                            }
                        }
                    },
                    icon = { Icon(AppIcons.Security, contentDescription = null) },
                    title = "其他第三方依赖",
                    supporting = "查看自动生成的依赖许可证列表",
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null
                        )
                    }
                )
            }
        }
    }

    if (showSingBoxNotice) {
        AlertDialog(
            onDismissRequest = { showSingBoxNotice = false },
            title = { Text("sing-box / libbox") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = singBoxNotice,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSingBoxNotice = false }) {
                    Text("关闭")
                }
            }
        )
    }
}
