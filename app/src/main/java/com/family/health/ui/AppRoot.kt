// 契约：docs/05-页面结构与交互.md §1 导航、§2 中央"＋"动作面板、§3 成员切换条
package com.family.health.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.family.health.data.AppViewModel
import com.family.health.feature.entry.ImportScreen
import com.family.health.feature.entry.MeasureFormScreen
import com.family.health.feature.meds.DailyManageScreen
import com.family.health.feature.meds.MedChangeScreen
import com.family.health.feature.meds.MedEditScreen
import com.family.health.feature.meds.MedHistoryScreen
import com.family.health.feature.meds.MedsScreen
import com.family.health.feature.mine.AboutScreen
import com.family.health.feature.mine.DevicesScreen
import com.family.health.feature.mine.ExportScreen
import com.family.health.feature.mine.MemberEditScreen
import com.family.health.feature.mine.MembersScreen
import com.family.health.feature.mine.MineScreen
import com.family.health.feature.mine.RemindersScreen
import com.family.health.feature.mine.TokenScreen
import com.family.health.feature.notes.NoteFormScreen
import com.family.health.feature.notes.NotesScreen
import com.family.health.feature.overview.OverviewScreen
import com.family.health.feature.records.EventDetailScreen
import com.family.health.feature.records.IndicatorHistoryScreen
import com.family.health.feature.records.RecordsScreen
import com.family.health.feature.records.ReportDetailScreen
import com.family.health.ui.components.Avatar
import com.family.health.ui.theme.FhColors
import kotlinx.coroutines.delay

object Routes {
    const val HOME = "home"
    const val RECORDS = "records"
    const val MEDS = "meds"
    const val MINE = "mine"
    const val NOTES = "notes"
    const val NOTE_FORM = "noteForm"
    const val EVENT = "event/{eventId}"
    const val REPORT = "report/{eventId}/{index}"
    const val INDICATOR = "indicator/{name}"
    const val MED_CHANGE = "medChange"
    const val MED_HISTORY = "medHistory"
    const val MED_EDIT = "medEdit/{medId}"
    const val DAILY = "daily"
    const val MEASURE_FORM = "measureForm/{type}"
    const val IMPORT = "import"
    const val MEMBERS = "members"
    const val MEMBER_EDIT = "memberEdit?memberId={memberId}"
    const val DEVICES = "devices"
    const val REMINDERS = "reminders"
    const val TOKEN = "token"
    const val EXPORT = "export"
    const val ACCOUNT = "account"
    const val ABOUT = "about"

    fun event(id: String) = "event/$id"
    fun report(eventId: String, index: Int) = "report/$eventId/$index"
    fun indicator(name: String) = "indicator/${android.net.Uri.encode(name)}"
    fun medEdit(id: String) = "medEdit/$id"
    fun measureForm(type: String) = "measureForm/$type"
    fun memberEdit(id: String?) = if (id == null) "memberEdit" else "memberEdit?memberId=$id"
}

val TAB_ROUTES = listOf(Routes.HOME, Routes.RECORDS, Routes.MEDS, Routes.MINE)

/** 切 Tab：清栈回到该 Tab 首屏（对应 prototype setTab） */
fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id)
        launchSingleTop = true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val configured by vm.configured.collectAsStateWithLifecycle()
    val nav = rememberNavController()

    // 未配置服务器：仅呈现首配页（契约：docs/02 §3.2 设备即身份，先 auth 后使用）
    if (!configured) {
        com.family.health.feature.setup.SetupScreen(vm)
        return
    }

    // 通知权限（API 33+ 运行时）；测量类本机闹钟与 reminders 表对齐
    if (android.os.Build.VERSION.SDK_INT >= 33) {
        val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { }
        LaunchedEffect(Unit) { launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
    }
    LaunchedEffect(ui.reminders, ui.currentMemberId) { vm.rescheduleMeasureReminders() }
    LaunchedEffect(ui.members) { vm.checkLowStockReminders() }
    var showPlus by rememberSaveable { mutableStateOf(false) }
    var showMemberSwitch by rememberSaveable { mutableStateOf(false) }

    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        containerColor = FhColors.Bg,
        topBar = {
            MemberBar(
                name = ui.currentMember.name,
                onSwitch = { showMemberSwitch = true },
            )
        },
        bottomBar = {
            TabBar(
                currentRoute = currentRoute,
                onTab = { nav.switchTab(it) },
                onPlus = { showPlus = true },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AppNavHost(nav, vm)

            // Toast（prototype #toast）
            AnimatedVisibility(
                visible = ui.toast != null,
                enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
            ) {
                Text(
                    ui.toast?.text.orEmpty(), color = Color.White, fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(FhColors.ToastBg)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
            ui.toast?.let { t ->
                LaunchedEffect(t.id) {
                    delay(1600)
                    vm.clearToast(t.id)
                }
            }
        }
    }

    // 中央"＋"动作面板（半屏弹层，契约 §2）
    if (showPlus) {
        ModalBottomSheet(onDismissRequest = { showPlus = false }) {
            Column(modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 26.dp)) {
                SheetGroup("日常") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SheetAction("🩺 记血压", Modifier.weight(1f)) {
                            showPlus = false; nav.navigate(Routes.measureForm("bp"))
                        }
                        SheetAction("🩸 记血糖", Modifier.weight(1f)) {
                            showPlus = false; nav.navigate(Routes.measureForm("glucose"))
                        }
                    }
                }
                SheetGroup("阶段性") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SheetAction("💊 记用药变化", Modifier.weight(1f)) {
                            showPlus = false; nav.navigate(Routes.MED_CHANGE)
                        }
                        SheetAction("🏥 记复查", Modifier.weight(1f), enabled = false) { }
                    }
                }
            }
        }
    }

    // 成员切换（居中弹层，契约 §1）
    if (showMemberSwitch) {
        AlertDialog(
            onDismissRequest = { showMemberSwitch = false },
            confirmButton = {},
            title = { Text("切换成员", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    ui.members.forEach { m ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.selectMember(m.id)
                                    showMemberSwitch = false
                                    nav.switchTab(currentTabOf(currentRoute))
                                }
                                .padding(vertical = 11.dp),
                        ) {
                            Avatar(m.name, 38)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(m.name, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    m.events.firstOrNull()?.let { "${it.checkupDate} 复查过" } ?: "暂无复查记录",
                                    fontSize = 12.sp, color = FhColors.Text2, modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            if (m.id == ui.currentMemberId) {
                                Text("✓", color = FhColors.Primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showMemberSwitch = false
                                nav.navigate(Routes.memberEdit(null))
                            }
                            .padding(vertical = 11.dp),
                    ) {
                        Avatar("＋", 38, FhColors.Tiny)
                        Spacer(Modifier.width(12.dp))
                        Text("添加成员", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
        )
    }
}

private fun currentTabOf(route: String?): String =
    TAB_ROUTES.firstOrNull { it == route } ?: Routes.HOME

@Composable
private fun SheetGroup(title: String, content: @Composable () -> Unit) {
    Text(
        title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FhColors.Text2,
        modifier = Modifier.padding(start = 2.dp, top = 14.dp, bottom = 8.dp),
    )
    content()
}

@Composable
private fun SheetAction(text: String, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(FhColors.Bg)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
    ) {
        Text(
            text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            color = if (enabled) FhColors.Text else FhColors.Tiny,
        )
    }
}

/** 成员切换条（常驻顶部：居中气泡名，点击切换成员） */
@Composable
private fun MemberBar(
    name: String,
    onSwitch: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .background(FhColors.Bg)
            .padding(top = 8.dp, bottom = 10.dp),
    ) {
        Text(
            name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FhColors.Primary,
            modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(FhColors.PrimarySoft)
                .clickable(onClick = onSwitch)
                .padding(horizontal = 18.dp, vertical = 5.dp),
        )
    }
}

private data class TabSpec(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    TabSpec(Routes.HOME, "概览", Icons.Filled.Home),
    TabSpec(Routes.RECORDS, "记录", Icons.Filled.List),
    TabSpec(Routes.MEDS, "用药", Icons.Filled.Favorite),
    TabSpec(Routes.MINE, "我的", Icons.Filled.Person),
)

/** 底部 4 Tab + 中央「＋」（契约 §1） */
@Composable
private fun TabBar(currentRoute: String?, onTab: (String) -> Unit, onPlus: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(top = 1.dp)
                .height(62.dp),
        ) {
            TABS.take(2).forEach { TabItem(it, currentRoute, onTab, Modifier.weight(1f)) }
            Spacer(Modifier.weight(1f))
            TABS.drop(2).forEach { TabItem(it, currentRoute, onTab, Modifier.weight(1f)) }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-18).dp)
                .size(52.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(FhColors.Primary)
                .clickable(onClick = onPlus),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "快捷录入", tint = Color.White, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun TabItem(spec: TabSpec, currentRoute: String?, onTab: (String) -> Unit, modifier: Modifier) {
    val on = currentRoute == spec.route
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.clickable { onTab(spec.route) },
    ) {
        Icon(
            spec.icon, contentDescription = spec.label,
            tint = if (on) FhColors.Primary else FhColors.Text2,
            modifier = Modifier.size(22.dp),
        )
        Text(
            spec.label, fontSize = 11.sp,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
            color = if (on) FhColors.Primary else FhColors.Text2,
        )
    }
}

@Composable
private fun AppNavHost(nav: NavHostController, vm: AppViewModel) {
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) { OverviewScreen(vm, nav) }
        composable(Routes.RECORDS) { RecordsScreen(vm, nav) }
        composable(Routes.MEDS) { MedsScreen(vm, nav) }
        composable(Routes.MINE) { MineScreen(vm, nav) }
        composable(Routes.NOTES) { NotesScreen(vm, nav) }
        composable(Routes.NOTE_FORM) { NoteFormScreen(vm, nav) }
        composable(Routes.EVENT) { back ->
            EventDetailScreen(vm, nav, back.arguments?.getString("eventId").orEmpty())
        }
        composable(Routes.REPORT) { back ->
            ReportDetailScreen(
                vm, nav,
                back.arguments?.getString("eventId").orEmpty(),
                back.arguments?.getString("index")?.toIntOrNull() ?: 0,
            )
        }
        composable(Routes.INDICATOR) { back ->
            IndicatorHistoryScreen(vm, nav, back.arguments?.getString("name").orEmpty())
        }
        composable(Routes.MED_CHANGE) { MedChangeScreen(vm, nav) }
        composable(Routes.MED_HISTORY) { MedHistoryScreen(vm, nav) }
        composable(Routes.MED_EDIT) { back ->
            MedEditScreen(vm, nav, back.arguments?.getString("medId").orEmpty())
        }
        composable(Routes.DAILY) { DailyManageScreen(vm, nav) }
        composable(Routes.MEASURE_FORM) { back ->
            MeasureFormScreen(vm, nav, back.arguments?.getString("type").orEmpty())
        }
        composable(Routes.IMPORT) { ImportScreen(vm, nav) }
        composable(Routes.MEMBERS) { MembersScreen(vm, nav) }
        composable(Routes.MEMBER_EDIT) { back ->
            MemberEditScreen(vm, nav, back.arguments?.getString("memberId"))
        }
        composable(Routes.DEVICES) { DevicesScreen(vm, nav) }
        composable(Routes.REMINDERS) { RemindersScreen(vm, nav) }
        composable(Routes.TOKEN) { TokenScreen(vm, nav) }
        composable(Routes.EXPORT) { ExportScreen(vm, nav) }
        composable(Routes.ACCOUNT) { com.family.health.feature.mine.AccountScreen(vm, nav) }
        composable(Routes.ABOUT) { AboutScreen(vm, nav) }
    }
}
