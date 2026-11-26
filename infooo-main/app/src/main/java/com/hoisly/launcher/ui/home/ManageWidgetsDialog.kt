package com.hoisly.launcher.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hoisly.launcher.R
import com.hoisly.launcher.data.AppWidgetEntry
import com.hoisly.launcher.data.PageWidgetEntry
import com.hoisly.launcher.data.WidgetEntry
import com.hoisly.launcher.data.WidgetStackEntry

/** A recovery path for removing widgets without long-pressing the widget on the home screen. */
@Composable
internal fun ManageWidgetsDialog(
    visible: Boolean,
    widgets: List<PageWidgetEntry>,
    onRemove: (String) -> Unit,
    onClose: () -> Unit,
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = onClose,
        shape = RoundedCornerShape(28.dp),
        title = {
            Column {
                Text(stringResource(R.string.edit_manage_widgets), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.manage_widgets_description),
                    fontWeight = FontWeight.Normal,
                )
            }
        },
        text = {
            if (widgets.isEmpty()) {
                Text(stringResource(R.string.manage_widgets_empty))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(widgets, key = { it.id }) { widget ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Widgets, contentDescription = null, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(widget.displayName(), fontWeight = FontWeight.Medium)
                                Text(widget.id, fontWeight = FontWeight.Normal)
                            }
                            IconButton(onClick = { onRemove(widget.id) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.manage_widget_remove))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text(stringResource(R.string.action_done)) }
        },
    )
}

internal fun List<PageWidgetEntry>.flattenForManagement(): List<PageWidgetEntry> =
    flatMap { widget -> listOf(widget) + ((widget as? WidgetStackEntry)?.items.orEmpty().flattenForManagement()) }

private fun PageWidgetEntry.displayName(): String = when (this) {
    is WidgetEntry -> kind.name
    is AppWidgetEntry -> provider.substringAfterLast('/').substringAfterLast('.').ifBlank { "App widget" }
    is WidgetStackEntry -> "Smart Stack (${items.size})"
}
