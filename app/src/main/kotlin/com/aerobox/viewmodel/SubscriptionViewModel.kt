package com.aerobox.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aerobox.core.subscription.ParseDiagnostics
import com.aerobox.data.model.Subscription
import com.aerobox.data.model.isLocalGroup
import com.aerobox.data.repository.ImportGroupTarget
import com.aerobox.data.repository.NoValidNodesException
import com.aerobox.data.repository.PreparedLocalImport
import com.aerobox.data.repository.SubscriptionImportResult
import com.aerobox.data.repository.SubscriptionRepository
import com.aerobox.data.repository.SubscriptionUpdateResult
import com.aerobox.data.repository.SubscriptionUpdateSummary
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

// Content that was successfully parsed but is waiting for the user to choose a
// target local group. The UI observes this and shows GroupPickerDialog.
data class PendingImport(
    val prepared: PreparedLocalImport,
    val suggestedName: String,
    val nodeCount: Int
)

// A subscription URL was detected via QR scan or external intent. We defer the
// actual fetch/import until the user confirms (and potentially renames it) in
// the subscription editor dialog.
data class PendingSubscriptionLink(
    val url: String,
    val suggestedName: String,
    val autoUpdate: Boolean,
    val updateInterval: Long
)

class SubscriptionViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val repository = SubscriptionRepository(appContext)

    val subscriptions = repository.getAllSubscriptions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val localGroups = repository.getLocalGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val ungroupedNodeCount = repository.observeUngroupedNodeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _uiMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val uiMessage: SharedFlow<String> = _uiMessage.asSharedFlow()

    private val _pendingImport = MutableStateFlow<PendingImport?>(null)
    val pendingImport: StateFlow<PendingImport?> = _pendingImport.asStateFlow()

    private val _pendingSubscriptionLink = MutableStateFlow<PendingSubscriptionLink?>(null)
    val pendingSubscriptionLink: StateFlow<PendingSubscriptionLink?> =
        _pendingSubscriptionLink.asStateFlow()

    fun addSubscription(
        name: String,
        url: String,
        autoUpdate: Boolean,
        updateInterval: Long
    ) {
        if (!isValidSubscriptionUrl(url)) {
            _uiMessage.tryEmit("订阅链接无效，请使用 HTTPS 链接")
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            val result = runCatching {
                repository.addSubscription(
                    name = name,
                    url = url,
                    autoUpdate = autoUpdate,
                    updateInterval = updateInterval
                )
            }
            result
                .onSuccess { importResult ->
                    _uiMessage.tryEmit(formatImportResultMessage(importResult))
                }
                .onFailure { error ->
                    _uiMessage.tryEmit("导入订阅失败：${toFriendlyError(error)}")
                }
            _isLoading.value = false
        }
    }

    fun deleteSubscription(subscription: Subscription) {
        viewModelScope.launch {
            repository.deleteSubscription(subscription)
            val tag = if (subscription.isLocalGroup()) "分组" else "订阅"
            _uiMessage.tryEmit("已删除${tag}：${subscription.name}")
        }
    }


    fun reorderSubscriptions(orderedSubscriptions: List<Subscription>) {
        viewModelScope.launch {
            repository.reorderSubscriptions(orderedSubscriptions)
        }
    }

    fun editSubscription(
        subscription: Subscription,
        name: String,
        url: String,
        autoUpdate: Boolean,
        updateInterval: Long
    ) {
        val isLocal = subscription.isLocalGroup()
        if (!isLocal && !isValidSubscriptionUrl(url)) {
            _uiMessage.tryEmit("订阅链接无效，请使用 HTTPS 链接")
            return
        }

        viewModelScope.launch {
            repository.updateSubscriptionDetails(
                subscription = subscription,
                name = name,
                url = url,
                autoUpdate = autoUpdate,
                updateInterval = updateInterval
            )
            val tag = if (isLocal) "分组" else "订阅"
            _uiMessage.tryEmit("已修改${tag}：${name.ifBlank { subscription.name }}")
        }
    }

    fun updateSubscription(subscription: Subscription) {
        if (subscription.isLocalGroup()) {
            _uiMessage.tryEmit("本地分组无需刷新")
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            val result = runCatching {
                repository.updateSubscription(subscription)
            }
            result
                .onSuccess { updateResult ->
                    _uiMessage.tryEmit(formatUpdateResultMessage(subscription.name, updateResult))
                }
                .onFailure { error ->
                    _uiMessage.tryEmit("更新订阅失败：${toFriendlyError(error)}")
                }
            _isLoading.value = false
        }
    }

    fun updateAllSubscriptions() {
        viewModelScope.launch {
            val subs = subscriptions.value
            val refreshable = subs.filterNot { it.isLocalGroup() }
            if (refreshable.isEmpty()) {
                _uiMessage.tryEmit("暂无可刷新的订阅")
                return@launch
            }

            _isLoading.value = true
            val results = repository.refreshAllSubscriptions(refreshable)
            val successResults = results.mapNotNull { it.getOrNull() }
            val successCount = successResults.size
            val failCount = results.size - successCount
            val lastError = results.asReversed().firstNotNullOfOrNull { it.exceptionOrNull() }
            val addedCount = successResults.sumOf { it.summary.addedCount }
            val updatedCount = successResults.sumOf { it.summary.updatedCount }
            val deletedCount = successResults.sumOf { it.summary.deletedCount }
            val metadataCount = successResults.count { it.metadataFromHeader }
            if (failCount == 0) {
                _uiMessage.tryEmit(
                    buildString {
                        append("订阅更新完成：").append(successCount).append(" 个")
                        append("（新增 ").append(addedCount)
                        append(" / 更新 ").append(updatedCount)
                        append(" / 删除 ").append(deletedCount).append("）")
                        if (metadataCount > 0) {
                            append("，").append(metadataCount).append(" 个同步流量/到期信息")
                        }
                    }
                )
            } else {
                val suffix = lastError?.let { "，${toFriendlyError(it)}" } ?: ""
                _uiMessage.tryEmit(
                    buildString {
                        append("订阅部分更新失败（成功 ").append(successCount)
                        append(" / 失败 ").append(failCount)
                        append("，新增 ").append(addedCount)
                        append(" / 更新 ").append(updatedCount)
                        append(" / 删除 ").append(deletedCount)
                        append("）")
                        if (metadataCount > 0) {
                            append("，").append(metadataCount).append(" 个同步流量/到期信息")
                        }
                        append(suffix)
                    }
                )
            }
            _isLoading.value = false
        }
    }

    // Handles QR scan results and external VIEW intents. Subscription URLs are
    // surfaced via [pendingSubscriptionLink] so the user can edit the name
    // before committing. Inline node content goes via [pendingImport] for
    // group selection.
    fun importExternalSource(
        source: String,
        nameHint: String = "",
        autoUpdate: Boolean = true,
        updateInterval: Long = SubscriptionRepository.DEFAULT_UPDATE_INTERVAL_MS
    ) {
        val trimmedSource = source.trim()
        if (trimmedSource.isBlank()) {
            _uiMessage.tryEmit("导入内容为空")
            return
        }

        if (repository.isValidRemoteSubscriptionUrl(trimmedSource)) {
            _pendingSubscriptionLink.value = PendingSubscriptionLink(
                url = trimmedSource,
                suggestedName = nameHint.trim(),
                autoUpdate = autoUpdate,
                updateInterval = updateInterval
            )
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            runCatching { repository.prepareLocalImport(trimmedSource, nameHint.trim()) }
                .onSuccess { prepared ->
                    _pendingImport.value = PendingImport(
                        prepared = prepared,
                        suggestedName = nameHint.trim().ifBlank { prepared.resolvedName.orEmpty() },
                        nodeCount = prepared.nodes.size
                    )
                }
                .onFailure { error ->
                    _uiMessage.tryEmit("导入失败：${toFriendlyError(error)}")
                }
            _isLoading.value = false
        }
    }

    // Called from NodeImportDialog — user has already picked a target in-dialog,
    // so this runs prepare + commit without a separate picker step.
    fun importNodeContent(
        source: String,
        target: ImportGroupTarget
    ) {
        val trimmedSource = source.trim()
        if (trimmedSource.isBlank()) {
            _uiMessage.tryEmit("节点内容为空")
            return
        }
        if (repository.isValidRemoteSubscriptionUrl(trimmedSource)) {
            _uiMessage.tryEmit("订阅链接请使用\u201C订阅链接\u201D入口导入")
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            runCatching {
                val prepared = repository.prepareLocalImport(trimmedSource, nameHintFrom(target))
                repository.commitLocalImport(prepared, target)
            }
                .onSuccess { importResult ->
                    _uiMessage.tryEmit(formatImportResultMessage(importResult))
                }
                .onFailure { error ->
                    _uiMessage.tryEmit("导入失败：${toFriendlyError(error)}")
                }
            _isLoading.value = false
        }
    }

    fun importLocalFile(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = runCatching {
                val resolver = appContext.contentResolver
                val displayName = resolver.query(uri, null, null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }
                val sizeBytes = resolver.query(uri, null, null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getLong(index) else null
                }
                if (sizeBytes != null && sizeBytes > 8L * 1024L * 1024L) {
                    throw IllegalStateException("本地文件过大，暂不支持超过 8 MB 的配置")
                }
                val content = resolver.openInputStream(uri)?.use { input -> input.readBytes() }
                    ?.toString(Charsets.UTF_8)
                    ?.removePrefix("\uFEFF")
                    ?.trim()
                    ?: throw IllegalStateException("无法读取本地文件")
                val baseName = displayName.orEmpty().substringBeforeLast('.')
                val prepared = repository.prepareLocalImport(content, baseName)
                prepared to baseName
            }
            result
                .onSuccess { (prepared, baseName) ->
                    _pendingImport.value = PendingImport(
                        prepared = prepared,
                        suggestedName = baseName.ifBlank { prepared.resolvedName.orEmpty() },
                        nodeCount = prepared.nodes.size
                    )
                }
                .onFailure { error ->
                    _uiMessage.tryEmit("导入本地文件失败：${toFriendlyError(error)}")
                }
            _isLoading.value = false
        }
    }

    fun confirmPendingImport(target: ImportGroupTarget) {
        val current = _pendingImport.value ?: return
        _pendingImport.value = null
        viewModelScope.launch {
            _isLoading.value = true
            runCatching { repository.commitLocalImport(current.prepared, target) }
                .onSuccess { importResult ->
                    _uiMessage.tryEmit(formatImportResultMessage(importResult))
                }
                .onFailure { error ->
                    _uiMessage.tryEmit("导入失败：${toFriendlyError(error)}")
                }
            _isLoading.value = false
        }
    }

    fun cancelPendingImport() {
        _pendingImport.value = null
    }

    fun confirmPendingSubscriptionLink(
        name: String,
        url: String,
        autoUpdate: Boolean,
        updateInterval: Long
    ) {
        _pendingSubscriptionLink.value = null
        addSubscription(
            name = name,
            url = url,
            autoUpdate = autoUpdate,
            updateInterval = updateInterval
        )
    }

    fun cancelPendingSubscriptionLink() {
        _pendingSubscriptionLink.value = null
    }

    private fun nameHintFrom(target: ImportGroupTarget): String {
        return when (target) {
            is ImportGroupTarget.New -> target.name
            is ImportGroupTarget.Existing -> ""
            is ImportGroupTarget.Ungrouped -> ""
        }
    }

    private fun isValidSubscriptionUrl(url: String): Boolean {
        return repository.isValidRemoteSubscriptionUrl(url)
    }

    private fun formatImportResultMessage(result: SubscriptionImportResult): String {
        val error = result.error
        if (error == null && result.nodeCount > 0) {
            val successPrefix = if (result.subscriptionId == 0L) {
                "导入成功：已添加 ${result.nodeCount} 个节点到未分组"
            } else {
                "导入成功：${result.nodeCount} 个节点"
            }
            return buildString {
                append(successPrefix)
                if (result.metadataFromHeader) {
                    append("，已读取订阅流量/到期信息")
                }
            }
        }

        return when {
            error?.message == SubscriptionRepository.NO_VALID_NODES_ERROR ->
                "导入失败：${friendlyNoValidNodesMessage(result.diagnostics)}"

            error?.message == SubscriptionRepository.LOCAL_GROUP_TARGET_INVALID_ERROR ->
                "导入失败：订阅分组不可作为导入目标"

            error != null ->
                "导入失败：${toFriendlyError(error)}"

            else ->
                "导入失败：${friendlyNoValidNodesMessage(result.diagnostics)}"
        }
    }

    private fun toFriendlyError(error: Throwable): String {
        return when (error) {
            is NoValidNodesException -> friendlyNoValidNodesMessage(error.diagnostics)
            is IllegalStateException ->
                when (error.message) {
                    SubscriptionRepository.NO_VALID_NODES_ERROR ->
                        "未解析到可用节点，请检查订阅格式"
                    SubscriptionRepository.LOCAL_GROUP_TARGET_INVALID_ERROR ->
                        "订阅分组不可作为导入目标"
                    else -> error.message?.takeIf { it.isNotBlank() } ?: "配置异常"
                }
            is UnknownHostException -> "无法连接订阅服务器，请检查网络或链接"
            is SocketTimeoutException -> "连接超时，请稍后重试"
            is SSLException -> "TLS/证书校验失败，请检查订阅链接"
            is IOException -> {
                val text = error.message.orEmpty()
                if (text.startsWith("HTTP ")) {
                    "订阅服务器返回 $text"
                } else if (text.isNotBlank()) {
                    text
                } else {
                    "网络异常，请稍后重试"
                }
            }
            else -> error.message?.takeIf { it.isNotBlank() } ?: "未知错误"
        }
    }

    private fun friendlyNoValidNodesMessage(diagnostics: ParseDiagnostics): String {
        val hints = diagnostics.reasonCounts.entries
            .sortedByDescending { it.value }
            .mapNotNull { (reason, _) -> diagnosticsHint(reason) }
            .distinct()
            .take(2)

        return if (hints.isEmpty()) {
            "未解析到可用节点，请检查订阅格式"
        } else {
            "未解析到可用节点。可能原因：${hints.joinToString("；")}"
        }
    }

    private fun diagnosticsHint(reason: String): String? {
        return when (reason) {
            "unsupported_subscription_content",
            "invalid_json_content",
            "invalid_clash_yaml" -> "订阅内容不是受支持的 sing-box、Clash 或节点链接格式"

            "missing_clash_proxies" -> "Clash 配置里没有 proxies 节点列表"

            "unsupported_json_type",
            "unsupported_clash_type",
            "unsupported_uri_scheme" -> "订阅里包含当前暂不支持的节点类型或链接协议"

            "unsupported_json_transport",
            "unsupported_clash_transport",
            "unsupported_json_network" -> "订阅里包含当前暂不支持的传输配置"

            "missing_json_endpoint",
            "missing_clash_endpoint" -> "部分节点缺少服务器地址或端口"

            "invalid_json_item",
            "invalid_clash_proxy_item" -> "订阅里的部分节点条目格式不正确"

            "invalid_or_unsupported_shadowsocks_uri",
            "invalid_or_unsupported_vmess_uri",
            "invalid_or_unsupported_vless_uri",
            "invalid_or_unsupported_trojan_uri",
            "invalid_or_unsupported_hysteria2_uri",
            "invalid_or_unsupported_tuic_uri",
            "invalid_or_unsupported_socks_uri",
            "invalid_or_unsupported_http_uri" -> "节点链接格式不正确，或包含当前暂不支持的参数"

            "informational_entry",
            "duplicate_entry" -> null

            else -> null
        }
    }

    private fun formatUpdateResultMessage(
        subscriptionName: String,
        result: SubscriptionUpdateResult
    ): String {
        val summaryText = formatSummary(result.summary)
        return buildString {
            append("订阅更新完成：").append(subscriptionName)
            if (summaryText.isNotBlank()) {
                append("（").append(summaryText).append("）")
            }
            if (result.metadataFromHeader) {
                append("，已同步流量/到期信息")
            }
        }
    }

    private fun formatSummary(summary: SubscriptionUpdateSummary): String {
        return if (summary.changedCount == 0) {
            "无节点变更"
        } else {
            buildString {
                append("新增 ").append(summary.addedCount)
                append(" / 更新 ").append(summary.updatedCount)
                append(" / 删除 ").append(summary.deletedCount)
            }
        }
    }
}
