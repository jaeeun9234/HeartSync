// app/src/main/java/com/example/heartsync/ui/components/BottomBar.kt
package com.example.heartsync.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.heartsync.util.Route

private data class BottomItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

// 필요에 맞게 탭 구성 수정하세요
private val items = listOf(
    BottomItem(Route.Home,"홈 화면", Icons.Default.Home),
    BottomItem(Route.Docs, "기록", Icons.Default.Folder),
    BottomItem(Route.Noti, "알림", Icons.Default.Notifications),
    BottomItem(Route.Profile, "내정보", Icons.Default.Person),
)

@Composable
fun BottomBar(navController: NavController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    NavigationBar {
        items.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        navController.navigate(item.route) {
                            // 백스택 과도 증가 방지 + 상태 복원
                            popUpTo(Route.MAIN) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
//                label = { Text(item.label) }
            )
        }
    }
}
