package com.thecutestgirl.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.thecutestgirl.app.sync.SyncForegroundPoller
import java.time.LocalDate
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.thecutestgirl.app.ui.components.GlassBottomBar
import com.thecutestgirl.app.ui.components.GlassTab
import com.thecutestgirl.app.ui.components.LocalAppBottomInset
import com.thecutestgirl.app.ui.event.EventEditScreen
import com.thecutestgirl.app.ui.event.EventScreen
import com.thecutestgirl.app.ui.meal.MealEditScreen
import com.thecutestgirl.app.ui.meal.MealScreen
import com.thecutestgirl.app.ui.outfit.OutfitEditScreen
import com.thecutestgirl.app.ui.outfit.OutfitScreen
import com.thecutestgirl.app.ui.outfit.OutfitUploadScreen
import com.thecutestgirl.app.ui.settings.SettingsMenuDrawer
import com.thecutestgirl.app.ui.settings.SettingsScreen
import com.thecutestgirl.app.ui.settings.SettingsScrim
import com.thecutestgirl.app.ui.settings.SettingsSection
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.launch
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

/**
 * 点通知进来要落到哪一页。
 *
 * @param route tab 路由（outfit / meal / event），由 [com.thecutestgirl.app.sync.SyncEngine.PartnerItem] 带过来
 * @param date 那条记录属于哪一天（ISO，如 2026-10-29）；为空就停在今天
 */
data class NotificationTarget(
    val route: String,
    val date: String,
    val recordId: String = ""
)

/**
 * APP 整体框架：底部玻璃拟态导航（穿搭 / 饮食 / 点滴）+ 页面路由。
 * 内容绘制在玻璃栏底下（滚动时可透过玻璃看到模糊的内容），玻璃栏悬浮在最上层。
 *
 * @param notificationTarget 从通知点进来时的落点；消费后调用 [onNotificationTargetConsumed] 清空
 */
@Composable
fun AppRoot(
    notificationTarget: NotificationTarget? = null,
    onNotificationTargetConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    // 模糊采样源状态：包住 NavHost 的 Box 是被模糊的内容，玻璃栏从它采样
    val hazeState = remember { HazeState() }
    // v4.3：全局设置入口 = 一个侧边菜单。三个列表页只留右上角菜单按钮，
    // 不再各自放齿轮 / 长按标题的隐藏入口。
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    // 每次打开抽屉递增：昵称/同步配置可能在设置页里改过，抽屉常驻组合，
    // 不主动刷新会一直显示旧值。
    var drawerRefreshKey by remember { mutableIntStateOf(0) }
    val openSettingsMenu: () -> Unit = {
        drawerRefreshKey++
        scope.launch { drawerState.open() }
    }
    val openSettingsSection: (SettingsSection) -> Unit = { section ->
        scope.launch { drawerState.close() }
        navController.navigate("settings?section=${section.key}") {
            launchSingleTop = true
        }
    }
    // 通知带过来的落点：只交给对应 route 的页面，消费后立刻清空，避免串到别的 tab
    var pendingTarget by remember { mutableStateOf<NotificationTarget?>(null) }
    val clearPendingTarget = { pendingTarget = null }
    val tabs = listOf(
        GlassTab("outfit", "穿搭", Icons.Outlined.Checkroom, Icons.Filled.Checkroom),
        GlassTab("meal", "饮食", Icons.Outlined.Restaurant, Icons.Filled.Restaurant),
        GlassTab("event", "点滴", Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite)
    )
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = tabs.any { it.route == currentRoute }
    val onSelect: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    // 点通知 → 切到对应 tab，并把日期带下去。
    // 为什么放在这里而不是 MainActivity：只有这里拿得到 navController。
    val context = LocalContext.current
    LaunchedEffect(notificationTarget) {
        val target = notificationTarget ?: return@LaunchedEffect
        // 通知进来要换页：抽屉先收起，避免面板盖住新页面
        drawerState.close()
        val route = target.route.takeIf { candidate -> tabs.any { it.route == candidate } } ?: "outfit"
        pendingTarget = target.copy(route = route)
        onSelect(route)
        onNotificationTargetConsumed()
    }

    // 前台轮询：App 开着的时候每 20 秒同步一次。
    // WorkManager 周期任务最短 15 分钟、Doze 下还会更长，靠它做不到「对方一传这边就能看到」。
    // repeatOnLifecycle(RESUMED) 保证退到后台就停，不占后台资源。
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            SyncForegroundPoller.runForever(context)
        }
    }

    // 全局侧边菜单：只做设置，不混入模块导航。
    // 打开手势只在三个列表页生效，编辑页/设置页不会被边缘滑动误触。
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = showBottomBar,
        scrimColor = SettingsScrim,
        drawerContent = {
            // NavHost 在抽屉之后组合，它的返回回调会先吃掉 Back；
            // 这里在抽屉打开时再注册一个（后注册先执行），保证返回键先关抽屉。
            if (drawerState.isOpen) {
                BackHandler { scope.launch { drawerState.close() } }
            }
            SettingsMenuDrawer(
                refreshKey = drawerRefreshKey,
                onClose = { scope.launch { drawerState.close() } },
                onOpenSection = openSettingsSection
            )
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (showBottomBar) {
                    GlassBottomBar(
                        hazeState = hazeState,
                        tabs = tabs,
                        currentRoute = currentRoute,
                        onSelect = onSelect
                    )
                }
            }
        ) { padding ->
            // 底部内边距不再消费在 NavHost 上（内容要延伸到玻璃栏底下），
            // 而是通过 CompositionLocal 提供给各页面自己预留滚动空间
            val bottomInset = padding.calculateBottomPadding()
            CompositionLocalProvider(LocalAppBottomInset provides bottomInset) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = padding.calculateTopPadding())
                        .haze(hazeState)
                ) {
                    // v2.5.3：页面切换加一层「轻微位移 + 淡入」的转场（SharedAxis 的简化版）。
                    // 位移只有 1/8 屏宽，够表达「进入了一层」，又不会让页面像滑块；
                    // 返回时反过来：新页面从 0.98 放大归位，旧页面右移淡出。
                    NavHost(
                        navController = navController,
                        startDestination = "outfit",
                        modifier = Modifier.fillMaxSize(),
                        enterTransition = {
                            slideInHorizontally(
                                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                                initialOffsetX = { it / 8 }
                            ) + fadeIn(tween(durationMillis = 220))
                        },
                        exitTransition = {
                            fadeOut(tween(durationMillis = 140)) +
                                scaleOut(targetScale = 0.98f, animationSpec = tween(durationMillis = 200))
                        },
                        popEnterTransition = {
                            fadeIn(tween(durationMillis = 200)) +
                                scaleIn(initialScale = 0.98f, animationSpec = tween(durationMillis = 240))
                        },
                        popExitTransition = {
                            slideOutHorizontally(
                                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                                targetOffsetX = { it / 8 }
                            ) + fadeOut(tween(durationMillis = 180))
                        }
                    ) {
                        // ---------- 穿搭（按天聚合） ----------
                        composable("outfit") {
                            OutfitScreen(
                                // 上传入口带上当前选中的日期，照片统一归到那一天
                                onAdd = { date -> navController.navigate("outfitUpload/$date") },
                                onOpen = { id -> navController.navigate("outfitEdit/$id") },
                                onOpenMenu = openSettingsMenu,
                                initialDate = pendingTarget
                                    ?.takeIf { it.route == "outfit" }
                                    ?.date
                                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                                onInitialTargetConsumed = clearPendingTarget
                            )
                        }
                        // 指定某一天的上传页：新增照片 + 展示/增删该日已有照片
                        composable(
                            route = "outfitUpload/{date}",
                            arguments = listOf(navArgument("date") { type = NavType.StringType })
                        ) { entry ->
                            OutfitUploadScreen(
                                dateArg = entry.arguments?.getString("date") ?: "",
                                onOpen = { id -> navController.navigate("outfitEdit/$id") },
                                onDone = { navController.popBackStack() }
                            )
                        }
                        composable("outfitEdit/{id}") { entry ->
                            OutfitEditScreen(
                                id = entry.arguments?.getString("id"),
                                onDone = { navController.popBackStack() }
                            )
                        }

                        // ---------- 饮食 ----------
                        composable("meal") {
                            MealScreen(
                                initialDate = pendingTarget
                                    ?.takeIf { it.route == "meal" }
                                    ?.date
                                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                                onInitialTargetConsumed = clearPendingTarget,
                                onOpenMeal = { date, mealType, recordId ->
                                    val base = "mealEdit/$date/$mealType"
                                    // id 用查询参数（?id=xx）传递，新增记录时可省略
                                    navController.navigate(if (recordId != null) "$base?id=$recordId" else base)
                                },
                                onOpenMenu = openSettingsMenu
                            )
                        }
                        composable(
                            route = "mealEdit/{date}/{mealType}?id={id}",
                            arguments = listOf(
                                navArgument("date") { type = NavType.StringType },
                                navArgument("mealType") { type = NavType.IntType },
                                navArgument("id") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                }
                            )
                        ) { entry ->
                            MealEditScreen(
                                date = entry.arguments?.getString("date") ?: "",
                                mealType = entry.arguments?.getInt("mealType") ?: 0,
                                recordId = entry.arguments?.getString("id")?.takeIf { it.isNotBlank() },
                                onDone = { navController.popBackStack() }
                            )
                        }

                        // ---------- 事件 ----------
                        composable("event") {
                            EventScreen(
                                onAdd = { navController.navigate("eventEdit") },
                                onOpen = { id -> navController.navigate("eventEdit/$id") },
                                onOpenMenu = openSettingsMenu,
                                initialDate = pendingTarget
                                    ?.takeIf { it.route == "event" }
                                    ?.date
                                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                                initialRecordId = pendingTarget
                                    ?.takeIf { it.route == "event" }
                                    ?.recordId
                                    ?.takeIf { it.isNotBlank() },
                                onInitialTargetConsumed = clearPendingTarget
                            )
                        }
                        composable("eventEdit") {
                            EventEditScreen(id = null, onDone = { navController.popBackStack() })
                        }
                        composable("eventEdit/{id}") { entry ->
                            EventEditScreen(
                                id = entry.arguments?.getString("id"),
                                onDone = { navController.popBackStack() }
                            )
                        }

                        // ---------- 设置 ----------
                        // 侧边菜单的每个分组都落到同一页，用 section 参数决定初始滚动位置。
                        composable(
                            route = "settings?section={section}",
                            arguments = listOf(
                                navArgument("section") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                }
                            )
                        ) { entry ->
                            SettingsScreen(
                                initialSection = SettingsSection.fromKey(
                                    entry.arguments?.getString("section")
                                ),
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
