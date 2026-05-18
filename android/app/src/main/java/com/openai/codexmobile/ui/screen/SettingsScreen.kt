package com.openai.codexmobile.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation

@Composable
fun SettingsScreen(
    paddingValues: PaddingValues,
    items: List<Pair<String, String>>,
    authTokenInput: String,
    onAuthTokenChange: (String) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineMedium,
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items.forEach { (label, value) ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = label, style = MaterialTheme.typography.labelLarge)
                        Text(text = value, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                OutlinedTextField(
                    value = authTokenInput,
                    onValueChange = onAuthTokenChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Bridge Token") },
                    visualTransformation = PasswordVisualTransformation(),
                )
                Text(
                    text = "如果 bridge 启用了 token 鉴权，这里填写 Bearer token 的值，不要包含 Bearer 前缀。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Button(onClick = onBack) {
            Text("返回")
        }
    }
}
