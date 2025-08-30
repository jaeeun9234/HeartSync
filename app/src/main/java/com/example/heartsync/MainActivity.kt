package com.example.heartsync   // ← 가능하면 전부 소문자로 통일(권장)

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.heartsync.ui.screens.LoginScreen
import com.example.heartsync.data.model.MeasureMode
import com.example.heartsync.data.model.SessionConfig
//import com.example.heartsync.service.MeasureService
import com.example.heartsync.ui.screens.HomeScreen
import com.example.heartsync.ui.screens.SplashSequence
import com.example.heartsync.util.Route          // ← util의 Route 하나만 import
import com.example.heartsync.viewmodel.AuthViewModel
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    private var keepSplash = true
    private val permissionLauncher =
        registerForActivityResult(RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { keepSplash }
        super.onCreate(savedInstanceState)
        requestRuntimePerms()

        window.decorView.postDelayed({ keepSplash = false }, 200)

        setContent {
            val nav = rememberNavController()
            val authVm: AuthViewModel = viewModel()
            val isLoggedIn = FirebaseAuth.getInstance().currentUser != null
            val nextRoute = if (isLoggedIn) Route.Home else Route.Login

            setContent{
                NavHost(navController = nav, startDestination = Route.Splash) {
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
                                //startMeasureService(cfg)
                            },
                            onStop = {
                                //stopService(Intent(this@MainActivity, MeasureService::class.java))
                            }
                        )
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

//    private fun startMeasureService(cfg: SessionConfig) {
//        val intent = Intent(this, MeasureService::class.java).apply {
//            putExtra(MeasureService.EXTRA_CFG, cfg)
//        }
//        ContextCompat.startForegroundService(this, intent)
//    }
}