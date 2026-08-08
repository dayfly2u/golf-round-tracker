package com.golfrecorder.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 라운드 중 필드에서 큰 터치 영역으로 타수를 올리고 내리는 카운터.
 *
 * [buttonColor]를 주면 그 항목의 +/- 버튼에만 색이 들어간다(라벨은 항상 기본색).
 * OB/해저드처럼 한 줄에 두 개를 나란히 두는 축소 크기는 [compact]로 켠다 —
 * 라벨은 고정 너비 없이 글자 길이만큼만 차지해서 짧은 라벨("OB") 뒤에 버튼이
 * 바로 붙는다.
 */
@Composable
fun StrokeStepper(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    minValue: Int = 0,
    buttonColor: Color? = null,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val valueWidth = if (compact) 20.dp else 56.dp
    val buttonPadding = if (compact) {
        PaddingValues(horizontal = 4.dp, vertical = 0.dp)
    } else {
        ButtonDefaults.ContentPadding
    }
    val buttonHeight = if (compact) 28.dp else ButtonDefaults.MinHeight
    // "−"와 "+" 글자의 실제 폭이 폰트상 서로 달라서, 폭을 안 정해주면 두 버튼
    // 크기가 미묘하게 달라 보인다. compact에서만 고정폭을 준다 — 기본 크기는
    // Material 기본 패딩이 넉넉해서 눈에 띄는 차이가 없었다.
    val buttonWidth = if (compact) 36.dp else null
    val buttonTextStyle = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge
    val buttonColors = if (buttonColor != null) {
        ButtonDefaults.buttonColors(containerColor = buttonColor)
    } else {
        ButtonDefaults.buttonColors()
    }
    val buttonModifier = if (buttonWidth != null) {
        Modifier.height(buttonHeight).width(buttonWidth)
    } else {
        Modifier.height(buttonHeight)
    }

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            modifier = if (compact) Modifier.padding(end = 4.dp) else Modifier.width(140.dp),
        )
        Button(
            onClick = { if (value > minValue) onValueChange(value - 1) },
            enabled = value > minValue,
            colors = buttonColors,
            contentPadding = buttonPadding,
            modifier = buttonModifier,
        ) { Text("−", style = buttonTextStyle) }
        Text(
            "$value",
            style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(valueWidth),
        )
        Button(
            onClick = { onValueChange(value + 1) },
            colors = buttonColors,
            contentPadding = buttonPadding,
            modifier = buttonModifier,
        ) { Text("+", style = buttonTextStyle) }
    }
}

/**
 * OB처럼 "몇 건 났는지"와 "그 건이 몇 타를 먹였는지"가 분리되는 벌타용 카운터.
 * [addAmounts]에 준 만큼 +N 버튼이 늘어난다(예: [1, 2] → "+1"/"+2" 두 개).
 * [value]는 항상 "건수"이고, 실제로 몇 타가 늘었는지는 호출자가 [onAdd]에서 처리한다.
 */
@Composable
fun PenaltyStepper(
    label: String,
    value: Int,
    onAdd: (amount: Int) -> Unit,
    onRemove: () -> Unit,
    canRemove: Boolean,
    buttonColor: Color,
    addAmounts: List<Int> = listOf(1),
    modifier: Modifier = Modifier,
) {
    val buttonPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
    val buttonHeight = 28.dp
    val buttonTextStyle = MaterialTheme.typography.labelSmall
    val buttonColors = ButtonDefaults.buttonColors(containerColor = buttonColor)

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            modifier = Modifier.padding(end = 4.dp),
        )
        Button(
            onClick = onRemove,
            enabled = canRemove,
            colors = buttonColors,
            contentPadding = buttonPadding,
            modifier = Modifier.height(buttonHeight),
        ) { Text("−", style = buttonTextStyle) }
        Text(
            "$value",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(20.dp),
        )
        addAmounts.forEach { amount ->
            Button(
                onClick = { onAdd(amount) },
                colors = buttonColors,
                contentPadding = buttonPadding,
                modifier = Modifier.height(buttonHeight).padding(start = 2.dp),
            ) { Text("+$amount", style = buttonTextStyle) }
        }
    }
}
