package com.example.speedmeter.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ButtonVariant { Default, Secondary, Outline, Ghost, Destructive }

/**
 * shadcn Button. Visual height is 44dp (size="lg"); Material still pads the touch
 * target to 48dp and supplies button semantics.
 */
@Composable
fun ShadButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Default,
    enabled: Boolean = true,
) {
    val c = Shad.colors
    val (container, content) = when (variant) {
        ButtonVariant.Default -> c.primary to c.primaryForeground
        ButtonVariant.Secondary -> c.secondary to c.secondaryForeground
        ButtonVariant.Outline -> c.background to c.foreground
        ButtonVariant.Ghost -> Color.Transparent to c.foreground
        ButtonVariant.Destructive -> c.destructive to c.destructiveForeground
    }
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 44.dp),
        enabled = enabled,
        shape = RoundedCornerShape(ShadRadius.md),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            // shadcn disabled = 50% opacity of the same variant.
            disabledContainerColor = container.copy(alpha = container.alpha * 0.5f),
            disabledContentColor = content.copy(alpha = 0.5f),
        ),
        border = if (variant == ButtonVariant.Outline) BorderStroke(1.dp, c.input) else null,
        elevation = null,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text, style = Shad.type.small)
    }
}

/** shadcn Card: hairline border, no shadow. */
@Composable
fun ShadCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ShadRadius.xl),
        color = Shad.colors.card,
        contentColor = Shad.colors.cardForeground,
        border = BorderStroke(1.dp, Shad.colors.border),
    ) {
        Column(Modifier.padding(20.dp), content = content)
    }
}

@Composable
fun CardHeader(title: String, description: String? = null) {
    Text(title, style = Shad.type.large, color = Shad.colors.cardForeground)
    if (description != null) {
        Spacer(Modifier.height(4.dp))
        Text(description, style = Shad.type.muted, color = Shad.colors.mutedForeground)
    }
}

enum class BadgeVariant { Default, Secondary, Outline, Destructive }

@Composable
fun ShadBadge(text: String, variant: BadgeVariant = BadgeVariant.Default, dot: Boolean = false) {
    val c = Shad.colors
    val (bg, fg) = when (variant) {
        BadgeVariant.Default -> c.primary to c.primaryForeground
        BadgeVariant.Secondary -> c.secondary to c.secondaryForeground
        BadgeVariant.Outline -> Color.Transparent to c.foreground
        BadgeVariant.Destructive -> c.destructive to c.destructiveForeground
    }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .then(if (variant == BadgeVariant.Outline) Modifier.border(1.dp, c.border, CircleShape) else Modifier)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(fg))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = Shad.type.label.copy(letterSpacing = 0.sp), color = fg)
    }
}

/**
 * shadcn Switch (44x24 track, 20dp thumb). Purely visual: put it inside [SettingRow],
 * which owns the toggle semantics and the full-width touch target.
 */
@Composable
fun ShadSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val c = Shad.colors
    val track by animateColorAsState(if (checked) c.primary else c.input, label = "track")
    val offset by animateDpAsState(if (checked) 20.dp else 0.dp, label = "thumb")
    Box(
        modifier = modifier
            .size(width = 44.dp, height = 24.dp)
            .clip(CircleShape)
            .background(track)
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .offset(x = offset)
                .size(20.dp)
                .clip(CircleShape)
                .background(c.background),
        )
    }
}

/** Label + description + switch, toggled anywhere on the row (one a11y node, Role.Switch). */
@Composable
fun SettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ShadRadius.md))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Shad.type.small, color = Shad.colors.foreground)
            Spacer(Modifier.height(2.dp))
            Text(description, style = Shad.type.muted, color = Shad.colors.mutedForeground)
        }
        ShadSwitch(checked)
    }
}

@Composable
fun ShadSeparator(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Shad.colors.border))
}

/** shadcn Progress: 8dp pill, primary on primary/20. Null = indeterminate. */
@Composable
fun ShadProgress(progress: Float?, modifier: Modifier = Modifier) {
    val c = Shad.colors
    val shaped = modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
    if (progress == null) {
        LinearProgressIndicator(modifier = shaped, color = c.primary, trackColor = c.primary.copy(alpha = 0.2f))
    } else {
        LinearProgressIndicator(
            progress = { progress },
            modifier = shaped,
            color = c.primary,
            trackColor = c.primary.copy(alpha = 0.2f),
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

/** shadcn Alert. Destructive keeps the text in the token colour, which passes AA here. */
@Composable
fun ShadAlert(
    title: String,
    description: String,
    destructive: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = Shad.colors
    val accent = if (destructive) c.destructive else c.foreground
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, if (destructive) c.destructive.copy(alpha = 0.5f) else c.border, RoundedCornerShape(ShadRadius.lg))
            .padding(16.dp),
    ) {
        Text(title, style = Shad.type.small, color = accent)
        Spacer(Modifier.height(4.dp))
        Text(description, style = Shad.type.muted, color = if (destructive) accent else c.mutedForeground)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}
