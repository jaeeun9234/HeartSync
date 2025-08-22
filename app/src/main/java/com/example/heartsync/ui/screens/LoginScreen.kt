package com.example.heartsync.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.heartsync.R


@Composable
fun LoginScreen(
    onLoginClick: (String, String) -> Unit,
    onRegisterClick: () -> Unit
) {
    var id by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var showPw by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(Modifier.height(48.dp))

        // 상단 가운데 로고 이미지
        Image(
            painter = painterResource(id = R.drawable.logo_arm),
            contentDescription = "HeartSync",
            modifier = Modifier
                .height(64.dp)
                .padding(top = 8.dp, bottom = 40.dp)
        )

        OutlinedTextField(
            value = id,
            onValueChange = { id = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("아이디(이메일)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            )
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = pw,
            onValueChange = { pw = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("비밀번호") },
            singleLine = true,
            visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val icon = if (showPw) Icons.Default.VisibilityOff else Icons.Default.Visibility
                IconButton(onClick = { showPw = !showPw }) {
                    Icon(icon, contentDescription = if (showPw) "비밀번호 숨기기" else "비밀번호 보기")
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            )
        )
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { onLoginClick(id, pw) },
            modifier = Modifier.fillMaxWidth(),
            enabled = id.isNotBlank() && pw.isNotBlank()
        ) {
            Text("로그인")
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onRegisterClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("회원가입")
        }
    }
}
