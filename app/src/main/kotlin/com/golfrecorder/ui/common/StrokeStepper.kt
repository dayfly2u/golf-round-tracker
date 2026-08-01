package com.golfrecorder.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** 라운드 중 필드에서 큰 터치 영역으로 타수를 올리고 내리는 카운터. */
@Composable
fun StrokeStepper(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    minValue: Int = 1,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(140.dp))
        Button(
            onClick = { if (value > minValue) onValueChange(value - 1) },
            enabled = value > minValue,
        ) { Text("−") }
        Text(
            "$value",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(56.dp),
        )
        Button(onClick = { onValueChange(value + 1) }) { Text("+") }
    }
}
