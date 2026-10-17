package com.luma.feature.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luma.core.ui.theme.LumaPurpleLight

/** Purple Space-Grotesk uppercase group label (titleSmall). Optional trailing Beta pill. */
@Composable
fun SettingsSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    beta: Boolean = false,
) {
    Row(
        modifier = modifier.padding(start = 4.dp, end = 4.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.SemiBold, letterSpacing = 1.0.sp,
            ),
            color = LumaPurpleLight,
        )
        if (beta) {
            Spacer(Modifier.width(8.dp))
            BetaPill() // internal composable already in this package
        }
    }
}
