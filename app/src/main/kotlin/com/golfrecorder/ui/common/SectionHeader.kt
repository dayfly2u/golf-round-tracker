package com.golfrecorder.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val SECTION_TITLE_COLOR = Color(0xFFC8E6C9)
private val SECTION_TITLE_TEXT_COLOR = Color(0xFF2E7D32)

@Composable
fun SectionHeader(text: String) {
    Text(
        text,
        fontWeight = FontWeight.Bold,
        color = SECTION_TITLE_TEXT_COLOR,
        modifier = Modifier
            .background(SECTION_TITLE_COLOR, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
