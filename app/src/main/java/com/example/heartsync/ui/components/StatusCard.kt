package com.example.heartsync.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun StatusCard(
    icon: String,           // "success" or "error"
    title: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Surface(shape = MaterialTheme.shapes.large, tonalElevation = 4.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ✅ / ❌ 아이콘
            if (icon == "success") {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32), // 초록색
                    modifier = Modifier.size(32.dp)
                )
            } else {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    tint = Color(0xFFC62828), // 빨간색
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(0.7f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(buttonText)
            }
        }
    }
}
