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
                    modifier = Modifier.fillMaxWidth().padding(vertical = 26.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    AppLogo(size = 76.dp)
                    Text("Hiosly", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.about_description), color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.about_version), color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(18.dp))
        }
        item {
            SettingsCard {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(stringResource(R.string.about_developer), color = Color.White.copy(alpha = 0.58f), fontSize = 12.sp)
                    Text("Muhammadkarrel", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(9.dp))
                    Text(stringResource(R.string.about_project_location), color = Color.White.copy(alpha = 0.58f), fontSize = 12.sp)
                    Text(stringResource(R.string.about_project_location_value), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
