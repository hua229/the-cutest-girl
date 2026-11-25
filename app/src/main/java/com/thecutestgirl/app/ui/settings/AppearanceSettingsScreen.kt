package com.thecutestgirl.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thecutestgirl.app.ui.theme.ThemeMode
import com.thecutestgirl.app.ui.theme.ThemeStore

/** 外观偏好只写本机；切换通过同一主题树重组，保留当前导航与草稿。 */
@Composable
fun AppearanceSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    ThemeStore.initialize(context)
    val selected by ThemeStore.mode.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    SettingsPage(title = "外观", icon = Icons.Outlined.Contrast, onBack = onBack) {
        SettingsCard {
            Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    val interactions = remember { MutableInteractionSource() }
                    val pressed by interactions.collectIsPressedAsState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .background(if (pressed) scheme.onSurface.copy(alpha = 0.04f) else Color.Transparent)
                            .selectable(
                                selected = selected == mode,
                                interactionSource = interactions,
                                indication = null,
                                role = Role.RadioButton,
                                onClick = { ThemeStore.setMode(context, mode) }
                            )
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(mode.label, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(16.dp))
                        if (selected == mode) {
                            Icon(Icons.Outlined.Check, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(24.dp))
                        } else {
                            Spacer(Modifier.size(24.dp))
                        }
                    }
                    if (index < ThemeMode.entries.lastIndex) SettingsDivider()
                }
            }
        }
    }
}
