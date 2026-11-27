package com.hoisly.launcher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.hoisly.launcher.R
import com.hoisly.launcher.ui.onboarding.AppLogo

/** Project credits and build information. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    SettingsPage(title = stringResource(R.string.row_about), onBack = onBack) {
        item {
            SettingsCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AppLogo(size = 120.dp)
                    Text("Hiosly", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.about_description), color = Color.White.copy(alpha = 0.78f), fontSize = 17.sp, lineHeight = 24.sp, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.about_version), color = Color.White.copy(alpha = 0.68f), fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        item {
            SettingsCard {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.about_developer), color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
                    Text("Muhammadkarrel", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(R.string.about_project_location), color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
                    Text(stringResource(R.string.about_project_location_value), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
