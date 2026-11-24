package com.hoisly.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hoisly.launcher.R
import com.hoisly.launcher.ui.lockscreen.PadState
import com.hoisly.launcher.ui.lockscreen.PatternPad
import com.hoisly.launcher.ui.lockscreen.isValidPattern
import kotlinx.coroutines.delay

private const val FEEDBACK_MILLIS = 800L

/** Draw a new unlock pattern twice; [onDone] gets it only when both drawings match. */
@Composable
internal fun PatternSetupDialog(onDone: (List<Int>) -> Unit, onCancel: () -> Unit) {
    var first by remember { mutableStateOf<List<Int>?>(null) }
    var padState by remember { mutableStateOf(PadState.Normal) }
    var message by remember { mutableStateOf(R.string.lock_pattern_draw) }
    var finished by remember { mutableStateOf<List<Int>?>(null) }

    LaunchedEffect(padState, finished) {
        if (padState == PadState.Normal) return@LaunchedEffect
        delay(FEEDBACK_MILLIS)
        val accepted = finished
        if (accepted != null) onDone(accepted) else padState = PadState.Normal
    }

    fun onDrawn(nodes: List<Int>) {
        val previous = first
        when {
            !isValidPattern(nodes) -> {
                message = R.string.lock_pattern_short
                padState = PadState.Warning
            }
            previous == null -> {
                first = nodes
                message = R.string.lock_pattern_confirm
                padState = PadState.Valid
            }
            previous == nodes -> {
                finished = nodes
                padState = PadState.Valid
            }
            else -> {
                first = null
                message = R.string.lock_pattern_mismatch
                padState = PadState.Warning
            }
        }
    }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Color(0xE6101418)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                stringResource(R.string.lock_pattern_title), color = Color.White,
                fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            )
            Text(
                stringResource(message), color = Color.White.copy(alpha = .8f),
                fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
            )
            PatternPad(state = padState, onComplete = { onDrawn(it) }, modifier = Modifier.width(280.dp))
            Text(
                stringResource(R.string.action_cancel), color = Color.White.copy(alpha = .8f), fontSize = 17.sp,
                modifier = Modifier.padding(top = 16.dp).clickable(onClick = onCancel).padding(16.dp),
            )
        }
    }
}
