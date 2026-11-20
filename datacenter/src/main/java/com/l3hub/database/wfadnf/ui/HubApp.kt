package com.l3hub.database.wfadnf.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.l3hub.database.wfadnf.core.ExportRequest
import com.l3hub.database.wfadnf.core.HubController
import com.l3hub.database.wfadnf.ui.components.HubNote
import com.l3hub.database.wfadnf.ui.components.HubPillTabs
import com.l3hub.database.wfadnf.ui.components.HubTabSpec
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Clock01
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.hugeicons.stroke.Repeat
import me.rerere.hugeicons.stroke.SmartPhone01

/** 「数据库」页的当前数据源；[snapshotId] 为空表示看运行中的库（只有结构，没有内容） */
data class DbSelection(val packageName: String, val snapshotId: String? = null)

private val hubTabs = listOf(
    HubTabSpec("构建", HugeIcons.SmartPhone01),
    HubTabSpec("快照", HugeIcons.Clock01),
    HubTabSpec("数据库", HugeIcons.File02),
    HubTabSpec("同步", HugeIcons.Repeat),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HubApp(controller: HubController) {
    val state by controller.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState { hubTabs.size }
    var database by remember { mutableStateOf<DbSelection?>(null) }

    // 选完位置才真正开始导出：文件名先按构建与时间生成，回调里只认那一次请求
    val createDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        // 请求记在控制器上：选位置期间界面被重建，回调仍然接得上
        val request = controller.pendingExport
        controller.pendingExport = null
        if (uri != null && request != null) {
            if (request.snapshotId != null) {
                controller.exportSnapshot(request.packageName, request.snapshotId, uri)
            } else {
                controller.exportLive(request.packageName, uri)
            }
        }
    }
    val requestExport: (ExportRequest) -> Unit = { request ->
        controller.pendingExport = request
        createDocument.launch(request.fileName())
    }
    val openDatabase: (DbSelection) -> Unit = { selection ->
        database = selection
        scope.launch { pagerState.animateScrollToPage(2) }
    }

    LaunchedEffect(state.message, state.error) {
        val text = state.error ?: state.message
        if (text != null) {
            snackbar.showSnackbar(text)
            controller.dismissMessage()
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("数据中心") },
                actions = {
                    if (state.scanning) {
                        CircularWavyProgressIndicator(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .size(24.dp),
                        )
                    } else {
                        IconButton(
                            onClick = controller::scan,
                            enabled = state.busyLabel == null,
                        ) {
                            Icon(HugeIcons.Refresh01, contentDescription = "重新扫描")
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            HubPillTabs(
                tabs = hubTabs,
                selected = pagerState.currentPage,
                onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                modifier = Modifier.padding(vertical = 4.dp),
            )
            if (state.busyLabel != null) {
                Box(Modifier.fillMaxWidth()) {
                    LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                }
                HubNote(
                    text = state.busyLabel.orEmpty(),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                when (page) {
                    0 -> BuildsScreen(
                        state = state,
                        controller = controller,
                        onExport = requestExport,
                        onOpenDatabase = openDatabase,
                    )

                    1 -> SnapshotsScreen(
                        state = state,
                        controller = controller,
                        onExport = requestExport,
                        onOpenDatabase = openDatabase,
                    )

                    2 -> DatabaseScreen(
                        state = state,
                        controller = controller,
                        selection = database,
                        onSelect = { selection -> database = selection },
                        onExport = requestExport,
                    )

                    else -> SyncScreen(state = state, controller = controller)
                }
            }
        }
    }}
