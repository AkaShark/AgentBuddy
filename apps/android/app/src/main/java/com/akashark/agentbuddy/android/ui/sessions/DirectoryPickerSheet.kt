package com.akashark.agentbuddy.android.ui.sessions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.akashark.agentbuddy.android.ui.LocalAppModel
import com.akashark.agentbuddy.android.ui.RecentDirectoryEntry
import com.akashark.agentbuddy.android.ui.RecentDirectoryStore
import com.akashark.agentbuddy.android.state.canBrowseDirectories
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.RemotePath

@Composable
fun DirectoryPickerSheet(
    servers: List<DirectoryPickerServerOption>,
    initialServerId: String,
    onSelect: (serverId: String, cwd: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val appModel = LocalAppModel.current
    val context = LocalContext.current
    val recentStore = remember(context) { RecentDirectoryStore(context) }
    val scope = rememberCoroutineScope()
    val serverIds = remember(servers) { servers.map { it.id } }

    var selectedServerId by remember {
        mutableStateOf(
            servers.firstOrNull { it.id == initialServerId }?.id
                ?: servers.firstOrNull()?.id
                ?: "",
        )
    }
    var currentPath by remember(selectedServerId) { mutableStateOf("") }
    var allEntries by remember(selectedServerId) { mutableStateOf<List<String>>(emptyList()) }
    var recentEntries by remember(selectedServerId) { mutableStateOf<List<RecentDirectoryEntry>>(emptyList()) }
    var isLoading by remember(selectedServerId) { mutableStateOf(true) }
    var errorMessage by remember(selectedServerId) { mutableStateOf<String?>(null) }
    var showHiddenDirectories by remember { mutableStateOf(false) }
    var searchQuery by remember(selectedServerId) { mutableStateOf("") }
    var showServerMenu by remember { mutableStateOf(false) }
    var showGoToPathDialog by remember { mutableStateOf(false) }
    var pathInput by remember { mutableStateOf("") }
    var remoteHomePath by remember(selectedServerId) { mutableStateOf("") }

    fun refreshRecentEntries(serverId: String) {
        recentEntries = recentStore.listForServer(serverId, limit = 8)
    }

    fun completeSelection(serverId: String, path: String) {
        recentEntries = recentStore.record(serverId, path, limit = 8)
        onSelect(serverId, path)
    }

    fun isDisconnectedError(error: Throwable): Boolean {
        val message = error.message?.lowercase().orEmpty()
        return "disconnected" in message ||
            ("transport error" in message && "not connected" in message)
    }

    fun isLocalServer(serverId: String): Boolean =
        appModel.snapshot.value?.servers?.firstOrNull { it.serverId == serverId }?.isLocal == true

    suspend fun resolveHome(serverId: String): String {
        if (isLocalServer(serverId)) {
            return com.akashark.agentbuddy.android.state.HomeAnchor.path(context)
        }
        val serverSnapshot = appModel.snapshot.value?.servers?.firstOrNull { it.serverId == serverId }
        if (serverSnapshot?.canBrowseDirectories != true) {
            errorMessage = "所选服务器未连接。"
            return "/"
        }
        return runCatching {
            appModel.client.resolveRemoteHome(serverId)
        }.getOrElse { error ->
            if (isDisconnectedError(error)) {
                errorMessage = "所选服务器未连接。"
            }
            "/"
        }
    }

    suspend fun listDirectory(serverId: String, path: String) {
        val normalizedPath = path.trim().ifEmpty { "/" }
        isLoading = true
        errorMessage = null

        if (isLocalServer(serverId)) {
            val dir = java.io.File(normalizedPath)
            val entries = runCatching { dir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList() }
            if (serverId != selectedServerId) return
            entries.onSuccess { names ->
                allEntries = names.sortedWith(String.CASE_INSENSITIVE_ORDER)
                currentPath = normalizedPath
            }.onFailure { err ->
                allEntries = emptyList()
                errorMessage = err.message ?: "无法列出目录。"
            }
            isLoading = false
            return
        }

        val serverSnapshot = appModel.snapshot.value?.servers?.firstOrNull { it.serverId == serverId }
        if (serverSnapshot?.canBrowseDirectories != true) {
            isLoading = false
            allEntries = emptyList()
            errorMessage = "所选服务器未连接。"
            return
        }

        val response = runCatching {
            appModel.client.listRemoteDirectory(serverId, normalizedPath)
        }

        if (serverId != selectedServerId) {
            return
        }

        response.onSuccess { result ->
            allEntries = result.directories
            currentPath = result.path
        }.onFailure { error ->
            allEntries = emptyList()
            errorMessage = if (isDisconnectedError(error)) {
                "所选服务器未连接。"
            } else {
                error.message ?: "无法列出目录。"
            }
        }
        isLoading = false
    }

    suspend fun loadInitialPath(serverId: String) {
        isLoading = true
        errorMessage = null
        allEntries = emptyList()
        currentPath = ""
        val home = resolveHome(serverId)
        if (serverId != selectedServerId) return
        remoteHomePath = home
        currentPath = home
        listDirectory(serverId, home)
    }

    fun pathSegments(path: String): List<Pair<String, String>> {
        val normalized = path.trim()
        if (normalized.isEmpty()) return listOf("/" to "/")
        val raw = RemotePath.parse(normalized).segments().map { seg -> seg.label to seg.fullPath }
        if (!isLocalServer(selectedServerId)) return raw
        // On local, hide every breadcrumb above `~` and relabel the anchor
        // itself as "~" so the trail reads `~ / projects / foo` rather than
        // `data / user / 0 / com.akashark.agentbuddy / files / codex-home / workspace / projects / foo`.
        val home = com.akashark.agentbuddy.android.state.HomeAnchor.path(context)
        val homeRoot = "~" to home
        val suffix = raw.dropWhile { it.second != home }.drop(1)
        return listOf(homeRoot) + suffix
    }


    fun navigateInto(name: String) {
        val nextPath = RemotePath.parse(currentPath).join(name).asString()
        scope.launch { listDirectory(selectedServerId, nextPath) }
    }

    fun navigateUp() {
        val nextPath = RemotePath.parse(currentPath).parent().asString()
        scope.launch { listDirectory(selectedServerId, nextPath) }
    }

    fun navigateToInputPath() {
        val target = com.akashark.agentbuddy.android.state.PathDisplay
            .expand(
                pathInput,
                isLocalServer(selectedServerId),
                context,
                remoteHome = remoteHomePath,
            )
            .trim()
        pathInput = ""
        showGoToPathDialog = false
        if (target.isNotEmpty()) {
            scope.launch { listDirectory(selectedServerId, target) }
        }
    }

    val selectedServer = remember(servers, selectedServerId) {
        servers.firstOrNull { it.id == selectedServerId }
    }
    val filteredEntries = remember(allEntries, searchQuery, showHiddenDirectories) {
        val hiddenFiltered = if (showHiddenDirectories) allEntries else allEntries.filterNot { it.startsWith(".") }
        val query = searchQuery.trim()
        if (query.isEmpty()) hiddenFiltered else hiddenFiltered.filter { it.contains(query, ignoreCase = true) }
    }

    LaunchedEffect(serverIds, initialServerId) {
        val currentServerId = selectedServerId
        if (currentServerId.isBlank() || !serverIds.contains(currentServerId)) {
            selectedServerId = servers.firstOrNull { it.id == initialServerId }?.id
                ?: servers.firstOrNull()?.id
                ?: ""
        }
    }

    LaunchedEffect(selectedServerId) {
        if (selectedServerId.isBlank()) return@LaunchedEffect
        searchQuery = ""
        refreshRecentEntries(selectedServerId)
        loadInitialPath(selectedServerId)
    }

    if (showGoToPathDialog) {
        AlertDialog(
            onDismissRequest = {
                showGoToPathDialog = false
                pathInput = ""
            },
            title = { Text("跳转到路径") },
            text = {
                OutlinedTextField(
                    value = pathInput,
                    onValueChange = { pathInput = it },
                    singleLine = true,
                    placeholder = { Text("D:\\Projects 或 /home/me/project") },
                )
            },
            confirmButton = {
                TextButton(onClick = { navigateToInputPath() }) {
                    Text("前往")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showGoToPathDialog = false
                    pathInput = ""
                }) {
                    Text("取消")
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.94f),
    ) {
        DirectoryPickerHeader(
            servers = servers,
            selectedServer = selectedServer,
            selectedServerId = selectedServerId,
            showServerMenu = showServerMenu,
            onShowServerMenuChange = { showServerMenu = it },
            onSelectServer = { selectedServerId = it },
            showHiddenDirectories = showHiddenDirectories,
            onToggleHiddenDirectories = { showHiddenDirectories = !showHiddenDirectories },
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            currentPath = currentPath,
            context = context,
            isLocalServer = { isLocalServer(it) },
            pathSegments = { pathSegments(it) },
            onNavigateUp = { navigateUp() },
            onOpenGoToPath = {
                pathInput = com.akashark.agentbuddy.android.state.PathDisplay.display(
                    currentPath,
                    isLocalServer(selectedServerId),
                    context,
                    remoteHome = remoteHomePath,
                )
                showGoToPathDialog = true
            },
            onOpenPath = { path -> scope.launch { listDirectory(selectedServerId, path) } },
        )

        DirectoryPickerContent(
            isLoading = isLoading,
            errorMessage = errorMessage,
            recentEntries = recentEntries,
            filteredEntries = filteredEntries,
            searchQuery = searchQuery,
            selectedServerId = selectedServerId,
            context = context,
            isLocalServer = { isLocalServer(it) },
            completeSelection = { serverId, path -> completeSelection(serverId, path) },
            navigateInto = { navigateInto(it) },
            onRetry = { scope.launch { listDirectory(selectedServerId, currentPath.ifEmpty { "/" }) } },
            onShowServerMenu = { showServerMenu = true },
            onClearRecents = { recentEntries = recentStore.clear(selectedServerId, limit = 8) },
        )

        DirectoryPickerFooter(
            currentPath = currentPath,
            onDismiss = onDismiss,
            onSelectCurrentPath = { completeSelection(selectedServerId, currentPath) },
        )
    }
}
