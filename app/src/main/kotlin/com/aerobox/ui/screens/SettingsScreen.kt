package com.aerobox.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aerobox.R
import com.aerobox.ui.theme.SingBoxVPNTheme
import com.aerobox.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val darkMode by viewModel.darkMode.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val autoConnect by viewModel.autoConnect.collectAsStateWithLifecycle()
    val autoUpdateSubscription by viewModel.autoUpdateSubscription.collectAsStateWithLifecycle()
    val showNotification by viewModel.showNotification.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    SettingsScreenContent(
        darkMode = darkMode,
        dynamicColor = dynamicColor,
        autoConnect = autoConnect,
        autoUpdateSubscription = autoUpdateSubscription,
        showNotification = showNotification,
        onDarkModeChange = { enabled -> scope.launch { viewModel.setDarkMode(enabled) } },
        onDynamicColorChange = { enabled -> scope.launch { viewModel.setDynamicColor(enabled) } },
        onAutoConnectChange = { enabled -> scope.launch { viewModel.setAutoConnect(enabled) } },
        onAutoUpdateSubscriptionChange = { enabled ->
            scope.launch { viewModel.setAutoUpdateSubscription(enabled) }
        },
        onShowNotificationChange = { enabled -> scope.launch { viewModel.setShowNotification(enabled) } }
    )
}

@Composable
private fun SettingsScreenContent(
    darkMode: Boolean,
    dynamicColor: Boolean,
    autoConnect: Boolean,
    autoUpdateSubscription: Boolean,
    showNotification: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onAutoConnectChange: (Boolean) -> Unit,
    onAutoUpdateSubscriptionChange: (Boolean) -> Unit,
    onShowNotificationChange: (Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            SectionHeader(title = stringResource(R.string.appearance))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            item {
            SettingItem(
                icon = {
                    Icon(Icons.Filled.ColorLens, contentDescription = null)
                },
                title = stringResource(R.string.dynamic_color),
                supporting = stringResource(R.string.android_12_plus),
                trailing = {
                    Switch(checked = dynamicColor, onCheckedChange = onDynamicColorChange)
                }
            )
            }
        }

        item {
            SettingItem(
                icon = { Icon(Icons.Filled.DarkMode, contentDescription = null) },
                title = stringResource(R.string.dark_mode),
                supporting = stringResource(R.string.appearance),
                trailing = {
                    Switch(checked = darkMode, onCheckedChange = onDarkModeChange)
                }
            )
        }

        item {
            SectionHeader(title = stringResource(R.string.connection_settings))
        }

        item {
            SettingItem(
                icon = { Icon(Icons.Filled.Power, contentDescription = null) },
                title = stringResource(R.string.auto_connect),
                supporting = stringResource(R.string.connection_settings),
                trailing = {
                    Switch(checked = autoConnect, onCheckedChange = onAutoConnectChange)
                }
            )
        }

        item {
            SettingItem(
                icon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                title = stringResource(R.string.auto_update_subscription),
                supporting = stringResource(R.string.connection_settings),
                trailing = {
                    Switch(
                        checked = autoUpdateSubscription,
                        onCheckedChange = onAutoUpdateSubscriptionChange
                    )
                }
            )
        }

        item {
            SettingItem(
                icon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                title = stringResource(R.string.show_notification),
                supporting = stringResource(R.string.connection_settings),
                trailing = {
                    Switch(checked = showNotification, onCheckedChange = onShowNotificationChange)
                }
            )
        }

        item {
            SectionHeader(title = stringResource(R.string.about))
        }

        item {
            SettingItem(
                icon = { Icon(Icons.Filled.Info, contentDescription = null) },
                title = stringResource(R.string.version),
                supporting = "1.0.0 (基于 sing-box 1.13.0)",
                trailing = {}
            )
        }

        item {
            SettingItem(
                icon = { Icon(Icons.Filled.Security, contentDescription = null) },
                title = stringResource(R.string.open_source_licenses),
                supporting = stringResource(R.string.about),
                trailing = { Icon(Icons.Filled.Power, contentDescription = null) }
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun SettingItem(
    icon: @Composable () -> Unit,
    title: String,
    supporting: String,
    trailing: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            leadingContent = icon,
            headlineContent = { Text(title) },
            supportingContent = { Text(supporting) },
            trailingContent = trailing
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    SingBoxVPNTheme {
        SettingsScreenContent(
            darkMode = false,
            dynamicColor = true,
            autoConnect = false,
            autoUpdateSubscription = false,
            showNotification = true,
            onDarkModeChange = {},
            onDynamicColorChange = {},
            onAutoConnectChange = {},
            onAutoUpdateSubscriptionChange = {},
            onShowNotificationChange = {}
        )
    }
}
