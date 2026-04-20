package com.aerobox.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aerobox.R
import com.aerobox.data.model.Subscription
import com.aerobox.data.repository.ImportGroupTarget

// Option shown in the group picker. Subscription-backed groups are never
// offered — those are refreshed from their remote URL, which would drop any
// manually-imported nodes we place into them.
sealed class GroupPickerOption {
    data object Ungrouped : GroupPickerOption()
    data class Existing(val subscription: Subscription) : GroupPickerOption()
    data object New : GroupPickerOption()
}

data class GroupPickerState(
    val option: GroupPickerOption,
    val newGroupName: String
) {
    fun toTarget(fallbackName: String): ImportGroupTarget {
        return when (val opt = option) {
            is GroupPickerOption.Ungrouped -> ImportGroupTarget.Ungrouped
            is GroupPickerOption.Existing -> ImportGroupTarget.Existing(opt.subscription.id)
            is GroupPickerOption.New -> {
                val name = newGroupName.trim()
                    .ifBlank { fallbackName.trim() }
                    .ifBlank { "本地分组" }
                ImportGroupTarget.New(name)
            }
        }
    }

    val isValid: Boolean
        get() = when (option) {
            is GroupPickerOption.New -> newGroupName.isNotBlank()
            else -> true
        }
}

@Composable
fun rememberGroupPickerState(
    suggestedName: String,
    localGroups: List<Subscription>,
    initialOption: GroupPickerOption? = null
): GroupPickerStateHolder {
    val defaultOption = remember(initialOption, suggestedName, localGroups) {
        initialOption ?: if (suggestedName.isNotBlank()) GroupPickerOption.New else GroupPickerOption.Ungrouped
    }
    var option by remember { mutableStateOf<GroupPickerOption>(defaultOption) }
    var newName by remember(suggestedName) { mutableStateOf(suggestedName) }
    return GroupPickerStateHolder(
        state = GroupPickerState(option, newName),
        onOptionChange = { option = it },
        onNewNameChange = { newName = it }
    )
}

data class GroupPickerStateHolder(
    val state: GroupPickerState,
    val onOptionChange: (GroupPickerOption) -> Unit,
    val onNewNameChange: (String) -> Unit
)

// Reusable section that lets the user pick where imported nodes should land.
// Used both by the standalone [GroupPickerDialog] (shown after local-file /
// QR / external import) and inline inside [NodeImportDialog].
@Composable
fun GroupPickerSection(
    holder: GroupPickerStateHolder,
    localGroups: List<Subscription>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.import_choose_group),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))

        GroupOptionRow(
            label = stringResource(R.string.group_ungrouped),
            supporting = null,
            selected = holder.state.option is GroupPickerOption.Ungrouped,
            onSelect = { holder.onOptionChange(GroupPickerOption.Ungrouped) }
        )

        if (localGroups.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 180.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 4.dp)
                ) {
                    localGroups.forEach { group ->
                        val current = holder.state.option
                        val selected = current is GroupPickerOption.Existing &&
                            current.subscription.id == group.id
                        GroupOptionRow(
                            label = group.name,
                            supporting = stringResource(
                                R.string.group_node_count_suffix,
                                group.nodeCount
                            ),
                            selected = selected,
                            onSelect = { holder.onOptionChange(GroupPickerOption.Existing(group)) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        GroupOptionRow(
            label = stringResource(R.string.group_new),
            supporting = null,
            selected = holder.state.option is GroupPickerOption.New,
            onSelect = { holder.onOptionChange(GroupPickerOption.New) }
        )

        if (holder.state.option is GroupPickerOption.New) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = holder.state.newGroupName,
                onValueChange = holder.onNewNameChange,
                label = { Text(stringResource(R.string.group_new_name_hint)) },
                singleLine = true,
                isError = holder.state.newGroupName.isBlank(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun GroupOptionRow(
    label: String,
    supporting: String?,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.height(0.dp))
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun GroupPickerDialog(
    nodeCount: Int,
    suggestedName: String,
    localGroups: List<Subscription>,
    onConfirm: (ImportGroupTarget) -> Unit,
    onDismiss: () -> Unit
) {
    val holder = rememberGroupPickerState(
        suggestedName = suggestedName,
        localGroups = localGroups
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_choose_group)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.import_node_count, nodeCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                GroupPickerSection(
                    holder = holder,
                    localGroups = localGroups
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(holder.state.toTarget(suggestedName)) },
                enabled = holder.state.isValid
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
