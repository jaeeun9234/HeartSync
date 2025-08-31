package com.example.heartsync.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.heartsync.util.Route
import com.example.heartsync.viewmodel.AuthEvent
import com.example.heartsync.viewmodel.AuthViewModel
import kotlinx.coroutines.flow.receiveAsFlow
import com.example.heartsync.ui.components.PasswordField
import kotlinx.coroutines.flow.collectLatest

@Composable
fun RegisterScreen(nav: NavHostController, vm: AuthViewModel) {
    var id by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var birth by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var pwConfirm by remember { mutableStateOf("") }
    val pwMismatch = pw.isNotBlank() && pwConfirm.isNotBlank() && pw != pwConfirm

    // 중복확인 결과/팝업 상태
    var idChecked by remember { mutableStateOf(false) }
    var idAvailable by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var dialogMsg by remember { mutableStateOf("") }

    // 에러 메시지
    var err by remember { mutableStateOf<String?>(null) }

    // 이벤트 수신
    LaunchedEffect(vm) {
        vm.events.receiveAsFlow().collectLatest { e ->
            when (e) {
                is AuthEvent.LoggedIn -> {
                    // 1) 스택에 Login 있으면 그걸로 복귀
                    val popped = nav.popBackStack(Route.Login, inclusive = false)
                    if (!popped) {
                        // 2) 없으면 새로 이동 (중복 방지)
                        nav.navigate(Route.Login) {
                            popUpTo(Route.Splash) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                }
                is AuthEvent.Error -> {
                    err = e.msg
                }
                is AuthEvent.IdCheckResult -> {
                    idChecked = true
                    idAvailable = e.available
                    dialogMsg = if (e.available) "사용 가능한 ID 입니다." else "이미 사용 중인 ID 입니다."
                    showDialog = true
                }
                else -> Unit
            }
        }
    }


    // ID가 변경되면 체크 상태 초기화(다시 확인하게)
    LaunchedEffect(id) {
        idChecked = false
        idAvailable = false
    }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {

        // ID 입력 + 중복확인 버튼
        Row(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = id,
                onValueChange = { id = it },
                label = { Text("ID") },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    if (id.isBlank()) {
                        dialogMsg = "ID를 입력해 주세요."
                        showDialog = true
                    } else {
                        vm.checkIdAvailability(id.trim())
                    }
                },
                modifier = Modifier.width(110.dp)
            ) { Text("중복 확인") }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(name, { name = it }, label={Text("이름")}, modifier=Modifier.fillMaxWidth())

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = phone,
            onValueChange = { input ->
                val digits = input.filter { it.isDigit() }.take(11) // 최대 11자리
                phone = when {
                    digits.length >= 11 -> "${digits.substring(0,3)}-${digits.substring(3,7)}-${digits.substring(7,11)}"
                    digits.length >= 7  -> "${digits.substring(0,3)}-${digits.substring(3,7)}-${digits.substring(7)}"
                    digits.length >= 3  -> "${digits.substring(0,3)}-${digits.substring(3)}"
                    else -> digits
                }
            },
            label = { Text("전화번호") },
            placeholder = { Text("010-1234-5678") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = birth,
            onValueChange = { input ->
                val digits = input.filter { it.isDigit() }.take(8) // 최대 8자리
                birth = when {
                    digits.length >= 8 -> "${digits.substring(0,4)}-${digits.substring(4,6)}-${digits.substring(6,8)}"
                    digits.length >= 6 -> "${digits.substring(0,4)}-${digits.substring(4,6)}-${digits.substring(6)}"
                    digits.length >= 4 -> "${digits.substring(0,4)}-${digits.substring(4)}"
                    else -> digits
                }
            },
            label = { Text("생년월일 (YYYY-MM-DD)") },
            placeholder = { Text("YYYY-MM-DD") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(email, { email = it }, label={Text("이메일")}, modifier=Modifier.fillMaxWidth())

        Spacer(Modifier.height(8.dp))
        PasswordField(
            value = pw,
            onValueChange = { pw = it },
            label = "비밀번호",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = pwConfirm,
            onValueChange = { pwConfirm = it },
            label = { Text("비밀번호 확인") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(), // 항상 가려짐
            isError = pwMismatch,
            supportingText = {
                if (pwMismatch) Text("비밀번호가 일치하지 않습니다.")
            }
        )


        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                when {
                    id.isBlank() || name.isBlank() || phone.isBlank() || birth.isBlank() || email.isBlank() || pw.isBlank() || pwConfirm.isBlank() -> {
                        dialogMsg = "모든 항목을 입력해 주세요."
                        showDialog = true
                    }
                    !idChecked -> {
                        dialogMsg = "ID 중복 확인을 진행해 주세요."
                        showDialog = true
                    }
                    !idAvailable -> {
                        dialogMsg = "이미 사용 중인 ID 입니다. 다른 ID를 입력해 주세요."
                        showDialog = true
                    }
                    pwMismatch -> {
                        dialogMsg = "비밀번호가 일치하지 않습니다."
                        showDialog = true
                    }
                    else -> {
                        vm.register(id.trim(), name.trim(), phone.trim(), birth.trim(), email.trim(), pw)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("OK") }


        err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }

    // 팝업(다이얼로그)
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text("확인") }
            },
            title = { Text("알림") },
            text = { Text(dialogMsg) }
        )
    }
}
