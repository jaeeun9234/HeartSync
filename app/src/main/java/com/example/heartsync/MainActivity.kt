package com.example.heartsync

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.heartsync.data.model.MeasureMode
import com.example.heartsync.data.model.SessionConfig
import com.example.heartsync.service.MeasureService
import com.example.heartsync.ui.screens.HomeScreen
import com.example.heartsync.ui.screens.LoginScreen
import com.example.heartsync.ui.screens.SplashSequence   // ⬅️ 새로 추가할 2단계 스플래시
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    private var keepSplash = true
    private val permissionLauncher = registerForActivityResult(RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1) 시스템 스플래시(1단계)
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { keepSplash }

        super.onCreate(savedInstanceState)
        requestRuntimePerms()

        // 1단계는 너무 오래 잡지 말고 아주 짧게만 유지 (워드마크가 2단계를 담당)
        window.decorView.postDelayed({ keepSplash = false }, 200)

        // 2) 메인 콘텐츠(네비게이션 + 2단계 스플래시 → 로그인/홈)
        setContent {
            val nav = rememberNavController()
            val isLoggedIn = FirebaseAuth.getInstance().currentUser != null
            val nextRoute = if (isLoggedIn) Route.HOME else Route.LOGIN

            NavHost(navController = nav, startDestination = Route.SPLASH) {
                // ② 워드마크 스플래시
                composable(Route.SPLASH) {
                    SplashSequence(nextRoute = nextRoute) { route ->
                        nav.navigate(route) {
                            popUpTo(Route.SPLASH) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
                // 로그인
                composable(Route.LOGIN) {
                    LoginScreen(
                        onLoginClick = { id, pw ->
                            // TODO: Firebase Auth 연결 후 성공 시 navigate
                            // FirebaseAuth.getInstance().signInWithEmailAndPassword(id, pw) ...
                            nav.navigate(Route.HOME) {
                                popUpTo(0); launchSingleTop = true
                            }
                        },
                        onRegisterClick = {
                            // TODO: RegisterScreen 연결 시 Route.REGISTER로 이동
                        }
                    )
                }
                // 홈
                composable(Route.HOME) {
                    HomeScreen(
                        onStart60s = {
                            val cfg = SessionConfig(mode = MeasureMode.SPOT, durationSec = 60)
                            startMeasureService(cfg)
                        },
                        onStop = {
                            stopService(Intent(this@MainActivity, MeasureService::class.java))
                        }
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

    private fun startMeasureService(cfg: SessionConfig) {
        val intent = Intent(this, MeasureService::class.java).apply {
            putExtra(MeasureService.EXTRA_CFG, cfg)
        }
        ContextCompat.startForegroundService(this, intent)
    }
}

// ⬇️ 라우트 상수(간단히 같은 파일에 넣어도 OK, 분리해도 OK)
object Route {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val HOME  = "home"
    // const val REGISTER = "register"  // 회원가입 붙일 때 추가
}
