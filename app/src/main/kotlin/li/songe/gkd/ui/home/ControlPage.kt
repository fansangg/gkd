package li.songe.gkd.ui.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.navigation.navigate
import li.songe.gkd.MainActivity
import li.songe.gkd.permission.notificationState
import li.songe.gkd.permission.requiredPermission
import li.songe.gkd.service.GkdAbService
import li.songe.gkd.service.ManageService
import li.songe.gkd.ui.component.AuthCard
import li.songe.gkd.ui.component.SettingItem
import li.songe.gkd.ui.component.TextSwitch
import li.songe.gkd.ui.destinations.ActivityLogPageDestination
import li.songe.gkd.ui.destinations.ClickLogPageDestination
import li.songe.gkd.ui.destinations.SlowGroupPageDestination
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.itemPadding
import li.songe.gkd.util.HOME_PAGE_URL
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.openUri
import li.songe.gkd.util.ruleSummaryFlow
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.throttle
import li.songe.gkd.util.tryStartActivity

val controlNav = BottomNavItem(label = "主页", icon = Icons.Outlined.Home)

@Composable
fun useControlPage(): ScaffoldExt {
    val context = LocalContext.current as MainActivity
    val navController = LocalNavController.current
    val vm = viewModel<HomeVm>()
    val latestRecordDesc by vm.latestRecordDescFlow.collectAsState()
    val subsStatus by vm.subsStatusFlow.collectAsState()
    val store by storeFlow.collectAsState()
    val ruleSummary by ruleSummaryFlow.collectAsState()

    val gkdAccessRunning by GkdAbService.isRunning.collectAsState()
    val manageRunning by ManageService.isRunning.collectAsState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val scrollState = rememberScrollState()
    return ScaffoldExt(navItem = controlNav,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(scrollBehavior = scrollBehavior, title = {
                Text(
                    text = controlNav.label,
                )
            }, actions = {
                IconButton(onClick = throttle { context.openUri(HOME_PAGE_URL) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = null,
                    )
                }
            })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(padding)
        ) {
            if (!gkdAccessRunning) {
                AuthCard(
                    title = "无障碍权限",
                    desc = "获取屏幕信息,匹配节点,执行操作",
                    onAuthClick = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.tryStartActivity(intent)
                    })
            } else {
                TextSwitch(
                    name = "服务开启",
                    desc = "根据规则匹配节点,执行操作",
                    checked = store.enableService,
                    onCheckedChange = {
                        storeFlow.value = store.copy(
                            enableService = it
                        )
                    })
            }

            TextSwitch(
                name = "常驻通知",
                desc = "通知栏显示运行状态及统计数据",
                checked = manageRunning && store.enableStatusService,
                onCheckedChange = vm.viewModelScope.launchAsFn<Boolean> {
                    if (it) {
                        requiredPermission(context, notificationState)
                        storeFlow.value = store.copy(
                            enableStatusService = true
                        )
                        ManageService.start(context)
                    } else {
                        storeFlow.value = store.copy(
                            enableStatusService = false
                        )
                        ManageService.stop(context)
                    }
                })

            SettingItem(
                title = "触发记录",
                subtitle = "如误触可在此快速定位关闭规则",
                onClick = {
                    navController.navigate(ClickLogPageDestination)
                }
            )

            if (store.enableActivityLog) {
                SettingItem(
                    title = "界面记录",
                    subtitle = "记录打开的应用及界面",
                    onClick = {
                        navController.navigate(ActivityLogPageDestination)
                    }
                )
            }

            if (ruleSummary.slowGroupCount > 0) {
                SettingItem(
                    title = "耗时查询-${ruleSummary.slowGroupCount}",
                    subtitle = "可能导致触发缓慢或更多耗电",
                    onClick = {
                        navController.navigate(SlowGroupPageDestination)
                    }
                )
            }
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .itemPadding()
            ) {
                Text(
                    text = subsStatus,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (latestRecordDesc != null) {
                    Text(
                        text = "最近点击: $latestRecordDesc",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }
}
