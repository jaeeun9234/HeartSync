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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.heartsync.data.model.MeasureMode
import com.example.heartsync.data.model.SessionConfig
import com.example.heartsync.ui.components.TopBar
import com.example.heartsync.ui.screens.HomeScreen
import com.example.heartsync.ui.screens.LoginScreen
import com.example.heartsync.ui.screens.RegisterScreen
import com.example.heartsync.ui.screens.SplashSequence
import com.example.heartsync.ui.themes.HeartSyncTheme
import com.example.heartsync.util.Route
import com.example.heartsync.viewmodel.AuthViewModel
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

                // 현재 라우트에 따라 TopBar 노출 여부 결정 (Splash에서는 숨김)
                val backStackEntry by nav.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route
                val showTopBar = (currentRoute ?: Route.Splash) != Route.Splash

                Scaffold(
                    topBar = { if (showTopBar) TopBar() }
                ) { inner ->
                    val isLoggedIn = FirebaseAuth.getInstance().currentUser != null
                    val nextRoute = if (isLoggedIn) Route.Home else Route.Login

                    NavHost(
                        navController = nav,
                        startDestination = Route.Splash,
                        modifier = androidx.compose.ui.Modifier.padding(inner)
                    ) {
                        composable(Route.Splash) {
                            SplashSequence(nextRoute = nextRoute) { route ->
                                nav.navigate(route) {
                                    popUpTo(Route.Splash) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        }
                        composable(Route.Login) {
                            LoginScreen(nav = nav, vm = authVm)
                        }
                        composable(Route.Home) {
                            HomeScreen(
                                onStart60s = {
                                    val cfg = SessionConfig(mode = MeasureMode.SPOT, durationSec = 60)
                                    // startMeasureService(cfg)
                                },
                                onStop = {
                                    // stopService(Intent(this@MainActivity, MeasureService::class.java))
                                }
                            )
                        }
                        composable(Route.Register)  {
                            RegisterScreen(nav = nav, vm = authVm)
                        }
                    }
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
