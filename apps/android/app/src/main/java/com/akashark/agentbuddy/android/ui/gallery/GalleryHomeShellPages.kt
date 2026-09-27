package com.akashark.agentbuddy.android.ui.gallery

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBottomSheet
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyContextChip
import com.akashark.agentbuddy.android.ui.home.HomeComposerCard
import com.akashark.agentbuddy.android.ui.homeshell.HomeComposerPill
import com.akashark.agentbuddy.android.ui.homeshell.HomeShellScaffold
import com.akashark.agentbuddy.android.ui.homeshell.HomeShellTab
import com.akashark.agentbuddy.android.ui.homeshell.hosts.HostActionHandlers
import com.akashark.agentbuddy.android.ui.homeshell.hosts.HostsHomeCallbacks
import com.akashark.agentbuddy.android.ui.homeshell.hosts.HostsHomeContent
import com.akashark.agentbuddy.android.ui.homeshell.newtask.NewTaskSheetContent
import com.akashark.agentbuddy.android.ui.homeshell.projects.ProjectsHomeCallbacks
import com.akashark.agentbuddy.android.ui.homeshell.projects.ProjectsHomeContent
import com.akashark.agentbuddy.android.ui.homeshell.tasks.TaskActionHandlers
import com.akashark.agentbuddy.android.ui.homeshell.tasks.TasksHeaderActions
import com.akashark.agentbuddy.android.ui.homeshell.tasks.TasksHomeCallbacks
import com.akashark.agentbuddy.android.ui.homeshell.tasks.TasksHomeContent
import com.akashark.agentbuddy.android.ui.homeshell.tasks.TasksHomeHeader
import com.akashark.agentbuddy.android.ui.homeshell.tasks.TasksHomeUiState
import com.akashark.agentbuddy.android.ui.homeshell.tasks.TasksHostAvailability
import com.akashark.agentbuddy.android.ui.homeshell.tasks.TasksSearchCallbacks
import com.akashark.agentbuddy.android.ui.homeshell.tasks.TasksSearchContent
import com.akashark.agentbuddy.android.ui.homeshell.tasks.TasksSearchUiState
import uniffi.codex_mobile_client.PinnedThreadKey

private val noHeaderActions = TasksHeaderActions({}, {}, {}, {}, {}, {}, onShowApps = {}, onShowTerminal = null)
private val noTaskCallbacks = TasksHomeCallbacks({}, {}, {}, {}, {})

/** The shell chrome around a gallery page, with local tab switching. */
@Composable
private fun GalleryShell(initialTab: HomeShellTab, showsPill: Boolean = true, page: @Composable (HomeShellTab) -> Unit) {
    var tab by remember { mutableStateOf(initialTab) }
    HomeShellScaffold(
        selectedTab = tab,
        onSelectTab = { tab = it },
        composerPill = if (showsPill) ({ HomeComposerPill(onCompose = {}, onVoice = {}) }) else null,
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        page = page,
    )
}

@Composable
private fun GalleryTasks(state: TasksHomeUiState) {
    GalleryShell(HomeShellTab.TASKS) {
        TasksHomeContent(
            state = state,
            headerActions = noHeaderActions,
            taskHandlers = TaskActionHandlers.None,
            callbacks = noTaskCallbacks,
        )
    }
}

/** 任务 with an item needing approval, a running card and recent rows. */
@Composable
fun GalleryHomePage() = GalleryTasks(GalleryHomeShellFixtures.tasksState())

/** 任务 with 「首页显示任务详情」 on. */
@Composable
fun GalleryHomeDetailPage() = GalleryTasks(GalleryHomeShellFixtures.tasksState(showsDetail = true))

/** 任务 before any host is paired. */
@Composable
fun GalleryHomeEmptyPage() = GalleryTasks(GalleryHomeShellFixtures.emptyTasksState(TasksHostAvailability.NO_HOSTS))

/** 任务 with a connected host but no tasks yet. */
@Composable
fun GalleryHomeNoTasksPage() = GalleryTasks(GalleryHomeShellFixtures.emptyTasksState(TasksHostAvailability.ONLINE))

/** 任务 while every saved host is offline. */
@Composable
fun GalleryHomeOfflinePage() = GalleryTasks(GalleryHomeShellFixtures.emptyTasksState(TasksHostAvailability.OFFLINE))

@Composable
fun GalleryHomeSearchPage() {
    GalleryShell(HomeShellTab.TASKS, showsPill = false) {
        TasksSearchContent(
            state = TasksSearchUiState(
                query = "登录",
                sessions = GalleryHomeShellFixtures.sessions,
                pinnedKeys = setOf(PinnedThreadKey(serverId = "mbp", threadId = "t-login")),
                runtimeKinds = listOf("codex", "claude"),
                selectedRuntimeKind = null,
                isRefreshing = false,
            ),
            callbacks = TasksSearchCallbacks({}, {}, {}, {}, {}, {}, {}),
            header = { TasksHomeHeader(GalleryHomeShellFixtures.hosts, null, noHeaderActions) },
            autoFocus = false,
        )
    }
}

@Composable
fun GalleryProjectsPage() {
    GalleryShell(HomeShellTab.PROJECTS) {
        ProjectsHomeContent(
            state = GalleryHomeShellFixtures.projectsState,
            callbacks = ProjectsHomeCallbacks({}, {}, {}, {}),
        )
    }
}

@Composable
fun GalleryHostsPage() {
    GalleryShell(HomeShellTab.HOSTS) {
        HostsHomeContent(
            state = GalleryHomeShellFixtures.hostsState,
            handlers = HostActionHandlers.None,
            callbacks = HostsHomeCallbacks({}, {}),
            showsBuildLabel = false,
        )
    }
}

/** The new-task sheet over the 任务 tab, with a draft. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GalleryNewTaskPage() {
    GalleryTasks(GalleryHomeShellFixtures.tasksState())
    var text by remember { mutableStateOf(TextFieldValue("把登录页的错误提示改得更友好，并补上单元测试。")) }
    BuddyBottomSheet(onDismissRequest = {}) {
        NewTaskSheetContent(
            onCancel = {},
            chips = {
                BuddyContextChip("MacBook Pro", onClick = {}, icon = Icons.Outlined.Laptop, trailingIcon = Icons.Outlined.KeyboardArrowDown)
                BuddyContextChip("AgentBuddy", onClick = {}, icon = Icons.Outlined.Folder, trailingIcon = Icons.Outlined.UnfoldMore)
                BuddyContextChip("gpt-5.5-codex · medium", onClick = {}, icon = Icons.Outlined.Memory, trailingIcon = Icons.Outlined.KeyboardArrowDown)
            },
            composer = {
                HomeComposerCard(
                    value = text,
                    onValueChange = { text = it },
                    onSend = {},
                    onAttach = {},
                    onDictation = {},
                    onExpand = {},
                )
            },
        )
    }
}
