package com.example.heartsync.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.heartsync.R
import com.example.heartsync.ui.themes.NavyHeader

@Composable
fun TopBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(NavyHeader), // 네이비 배경
        contentAlignment = Alignment.Center // 가운데 정렬
    ) {
        // 중앙 로고 이미지
        Image(
            painter = painterResource(id = R.drawable.topbar),
            contentDescription = "앱 로고",
            modifier = Modifier
                .height(36.dp)   // 원하는 크기
                .wrapContentWidth()
        )
    }
}
