package com.hoisly.launcher.panels.notifications

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hoisly.launcher.R
import com.hoisly.launcher.ui.theme.Motion
import com.hoisly.launcher.ui.theme.Tokens

private data class NotificationGroup(val packageName: String, val label: String, val items: List<CenterNotification>)

/**
 * The list of notifications, grouped by app: a stack that expands on tap, cards that swipe away,
 * a Clear All. Opening a card runs the notification's own intent; dismissing cancels it in the
 * system through [NotificationCenterFeed].
 */
@Composable
internal fun NotificationCenterPanel(
    notifications: List<CenterNotification>,
    hideContent: Boolean,
    hasAccess: Boolean,
    onAllowAccess: () -> Unit,
    onOpen: (CenterNotification) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups = remember(notifications) {
        notifications.groupBy { it.packageName }.map { (pkg, list) -> NotificationGroup(pkg, list.first().appLabel, list) }
    }
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.notification_center_title), color = Color.White,
                fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
            )
            val clearable = notifications.filter { it.clearable }
            if (clearable.isNotEmpty()) {
                PillButton(stringResource(R.string.notification_clear_all)) {
                    NotificationCenterFeed.dismissAll(clearable.map { it.key })
                }
            }
        }
        when {
            !hasAccess -> Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                PillButton(stringResource(R.string.notification_allow_access), onAllowAccess)
            }
            groups.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.notification_empty), color = Color.White.copy(alpha = 0.55f), fontSize = 17.sp)
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                items(groups, key = { it.packageName }) { group ->
                    GroupCard(group, hideContent, onOpen)
                }
                item { Box(Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
private fun GroupCard(group: NotificationGroup, hideContent: Boolean, onOpen: (CenterNotification) -> Unit) {
    var expanded by rememberSaveable(group.packageName) { mutableStateOf(false) }
    val stacked = group.items.size > 1 && !expanded
    Column(Modifier.animateContentSize(Motion.settle()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (expanded) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(group.label, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                PillButton(stringResource(R.string.notification_show_less)) { expanded = false }
            }
        }
        val shown = if (expanded) group.items else group.items.take(1)
        shown.forEach { item ->
            // Stable per notification: a dismissed card does not hand its offset to the next one.
            androidx.compose.runtime.key(item.key) {
                SwipeToClear(enabled = item.clearable, onClear = { NotificationCenterFeed.dismiss(item.key) }) {
                    NotificationCard(
                        notification = item,
                        hideContent = hideContent,
                        onClick = { if (stacked) expanded = true else onOpen(item) },
                    )
                }
            }
        }
        if (stacked) {
            val more = group.items.size - 1
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.width(250.dp).height(10.dp).clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                        .background(Tokens.MenuBackground.copy(alpha = 0.7f)),
                )
            }
            Text(
                pluralText(more), color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp,
                modifier = Modifier.padding(start = 4.dp).clickable(role = Role.Button) { expanded = true },
            )
        }
    }
}

@Composable
private fun pluralText(more: Int): String =
    androidx.compose.ui.res.pluralStringResource(R.plurals.notification_more, more, more)

@Composable
internal fun PillButton(text: String, onClick: () -> Unit) {
    Text(
        text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.2f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}
