package com.example.heartsync

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
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

                Scaffold(topBar = { if (showTopBar) TopBar() }) { inner ->
                    AppNav(
                        navController = nav,
                        modifier = Modifier.padding(inner),
                        authVm = authVm
                    )
                }
            }
        }
    }

    @Composable
    private fun AppNav(
        navController: NavHostController,
        modifier: Modifier = Modifier,
        authVm: AuthViewModel
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

            // 3) 메인 그래프 (여기서 BleViewModel "공유")
            navigation(startDestination = Route.Home, route = Route.MAIN) {

                // Home (사진 1/4)
                composable(Route.Home) { backStackEntry ->
                    // ★ MAIN 그래프의 ViewModelStoreOwner를 사용하여 공유 인스턴스 획득
                    val parentEntry = remember(backStackEntry) {
                        navController.getBackStackEntry(Route.MAIN)
                    }
                    val appCtx = LocalContext.current.applicationContext
                    val bleVm: BleViewModel = viewModel(
                        viewModelStoreOwner = parentEntry,
                        factory = BleViewModel.provideFactory(appCtx)
                    )

                    HomeScreen(
                        onClickBle = { navController.navigate(Route.BLE_CONNECT) },
                        bleVm = bleVm
                    )
                }

                // BLE 연결 화면 (사진 2/3)
                composable(Route.BLE_CONNECT) { backStackEntry ->
                    val parentEntry = remember(backStackEntry) {
                        navController.getBackStackEntry(Route.MAIN)
                    }
                    val appCtx = LocalContext.current.applicationContext
                    val bleVm: BleViewModel = viewModel(
                        viewModelStoreOwner = parentEntry,
                        factory = BleViewModel.provideFactory(appCtx)
                    )

                    BleConnectScreen(
                        vm = bleVm,
                        onConnected = { navController.popBackStack() } // 연결 후 홈(사진 4)
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
