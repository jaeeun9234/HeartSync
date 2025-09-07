// app/src/main/java/com/example/heartsync/MainActivity.kt
package com.example.heartsync

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import com.example.heartsync.ui.components.BottomBar
import com.example.heartsync.ui.components.TopBar
import com.example.heartsync.ui.screens.BleConnectScreen
import com.example.heartsync.ui.screens.HomeScreen
import com.example.heartsync.ui.screens.LoginScreen
import com.example.heartsync.ui.screens.RegisterScreen
import com.example.heartsync.ui.screens.SplashSequence
import com.example.heartsync.ui.themes.HeartSyncTheme
import com.example.heartsync.util.Route
import com.example.heartsync.viewmodel.AuthViewModel
import com.example.heartsync.viewmodel.BleViewModel
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {

    // ★ Activity 범위에서 단 하나의 BLE ViewModel 생성(앱 전체 공유)
    private val bleVm: BleViewModel by viewModels()

    private val permissionLauncher =
        registerForActivityResult(RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRuntimePerms()

        setContent {
            HeartSyncTheme {
                val nav = rememberNavController()
                val authVm: AuthViewModel = viewModel()

                // Splash에서는 TopBar 숨김
                val backStackEntry by nav.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route
                val showTopBar = (currentRoute ?: Route.Splash) != Route.Splash

                // ✅ BottomBar: 기본은 Splash만 제외하고 표시
                val showBottomBar = (currentRoute ?: Route.Splash) != Route.Splash
                // 🔄 만약 BLE 연결 화면에서도 숨기고 싶다면 이렇게 바꾸면 됨:
                // val showBottomBar = currentRoute !in setOf(Route.Splash, Route.BLE_CONNECT)

                Scaffold(
                    topBar = { if (showTopBar) TopBar(/* onLogoClick = { nav.navigate(Route.Home) } */) },
                    bottomBar = { if (showBottomBar) BottomBar(nav) }
                ) { inner ->
                    AppNav(
                        navController = nav,
                        modifier = Modifier.padding(inner),
                        authVm = authVm,
                        bleVm = bleVm
                    )
                }
            }
        }
    }

    @Composable
    private fun AppNav(
        navController: NavHostController,
        modifier: Modifier = Modifier,
        authVm: AuthViewModel,
        bleVm: BleViewModel,                 // ★ 전달받은 전역 BLE VM
    ) {
        val isLoggedIn = FirebaseAuth.getInstance().currentUser != null
        val nextRoute = if (isLoggedIn) Route.MAIN else Route.Login

        NavHost(
            navController = navController,
            startDestination = Route.Splash,
            route = Route.ROOT,
            modifier = modifier
        ) {
            // 1) 스플래시
            composable(Route.Splash) {
                SplashSequence(nextRoute = nextRoute) { route ->
                    navController.navigate(route) {
                        popUpTo(Route.Splash) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }

            // 2) 인증
            composable(Route.Login) { LoginScreen(nav = navController, vm = authVm) }
            composable(Route.Register) { RegisterScreen(nav = navController, vm = authVm) }

            // 3) 메인 그래프 (여기서도 같은 bleVm을 그대로 전달)
            navigation(startDestination = Route.Home, route = Route.MAIN) {

                // Home
                composable(Route.Home) {
                    HomeScreen(
                        onClickBle = { navController.navigate(Route.BLE_CONNECT) },
                        bleVm = bleVm
                    )
                }

                // BLE 연결 화면
                composable(Route.BLE_CONNECT) {
                    BleConnectScreen(
                        vm = bleVm,
                        onConnected = { navController.popBackStack() } // 연결 후 이전 화면으로
                    )
                }
            }
        }
    }

    private fun requestRuntimePerms() {
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= 31) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
        permissionLauncher.launch(perms)
    }
}
