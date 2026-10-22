package com.example.speedmeter.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * shadcn Tabs-style segmented control for 2–3 short options: muted track, selected
 * segment raised on the card colour. Each segment is a radio button for a11y.
 */
@Composable
fun <T> ShadToggleGroup(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Shad.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ShadRadius.lg))
            .background(c.muted)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val bg by animateColorAsState(if (isSelected) c.background else Color.Transparent, label = "segment")
            Box(
                modifier = Modifier
                    .weight(1f)
                    // 40dp segment + 4dp track padding each side = 48dp touch target.
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(ShadRadius.md))
                    .background(bg)
                    .then(
                        if (isSelected) Modifier.border(1.dp, c.border, RoundedCornerShape(ShadRadius.md)) else Modifier,
                    )
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option) })
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = Shad.type.small,
                    color = if (isSelected) c.foreground else c.mutedForeground,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** shadcn RadioGroup: vertical list, each whole row tappable (48dp min) and announced as a radio. */
@Composable
fun <T> ShadRadioGroup(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Shad.colors
    Column(modifier.selectableGroup()) {
        options.forEach { option ->
            val isSelected = option == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(ShadRadius.md))
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option) }),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(18.dp)
                        .border(1.dp, if (isSelected) c.primary else c.mutedForeground, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) Box(Modifier.size(9.dp).clip(CircleShape).background(c.primary))
                }
                Text(label(option), style = Shad.type.small, color = c.foreground)
            }
        }
    }
}
