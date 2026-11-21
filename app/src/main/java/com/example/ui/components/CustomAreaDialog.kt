package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.NormalizedRect
import com.example.data.VideoResolution
import com.example.ui.theme.JetBrainsMonoFamily
import kotlin.math.roundToInt

/**
 * Interactive modal dialog that takes direct input from the user to choose a custom recording area
 * via both draggable corner handles and exact percentage coordinates (Left, Top, Width, Height).
 */
@Composable
fun CustomAreaInputDialog(
    initialRect: NormalizedRect,
    resolution: VideoResolution,
    onDismiss: () -> Unit,
    onApply: (NormalizedRect) -> Unit
) {
    val startRect = remember(initialRect) {
        if (initialRect.isFullScreen()) NormalizedRect(0.10f, 0.14f, 0.90f, 0.80f)
        else initialRect.sanitized()
    }

    var workingRect by remember { mutableStateOf(startRect) }
    var leftInput by remember { mutableStateOf((startRect.left * 100f).roundToInt().toString()) }
    var topInput by remember { mutableStateOf((startRect.top * 100f).roundToInt().toString()) }
    var widthInput by remember { mutableStateOf((startRect.width * 100f).roundToInt().toString()) }
    var heightInput by remember { mutableStateOf((startRect.height * 100f).roundToInt().toString()) }

    fun syncInputsFromRect(r: NormalizedRect) {
        val safe = r.sanitized()
        workingRect = safe
        leftInput = (safe.left * 100f).roundToInt().toString()
        topInput = (safe.top * 100f).roundToInt().toString()
        widthInput = (safe.width * 100f).roundToInt().toString()
        heightInput = (safe.height * 100f).roundToInt().toString()
    }

    fun applyInputsToRect(lStr: String, tStr: String, wStr: String, hStr: String) {
        val lPct = (lStr.toIntOrNull() ?: (workingRect.left * 100f).roundToInt()).coerceIn(0, 80)
        val tPct = (tStr.toIntOrNull() ?: (workingRect.top * 100f).roundToInt()).coerceIn(0, 80)
        val maxW = (100 - lPct).coerceAtLeast(20)
        val maxH = (100 - tPct).coerceAtLeast(20)
        val wPct = (wStr.toIntOrNull() ?: (workingRect.width * 100f).roundToInt()).coerceIn(20, maxW)
        val hPct = (hStr.toIntOrNull() ?: (workingRect.height * 100f).roundToInt()).coerceIn(20, maxH)
        workingRect = NormalizedRect(
            left = lPct / 100f,
            top = tPct / 100f,
            right = ((lPct + wPct).coerceAtMost(100)) / 100f,
            bottom = ((tPct + hPct).coerceAtMost(100)) / 100f
        ).sanitized()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Crop,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.custom_area_dialog_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    TextButton(
                        onClick = { syncInputsFromRect(NormalizedRect(0.10f, 0.14f, 0.90f, 0.80f)) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("custom_area_reset_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.custom_area_reset),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.custom_area_dialog_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Quick Region Presets
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val quickPresets = listOf(
                        "Center" to NormalizedRect(0.10f, 0.18f, 0.90f, 0.78f),
                        "Top Half" to NormalizedRect(0.0f, 0.0f, 1.0f, 0.50f),
                        "Bottom Half" to NormalizedRect(0.0f, 0.50f, 1.0f, 1.0f),
                        "Square" to NormalizedRect(0.05f, 0.25f, 0.95f, 0.75f)
                    )
                    quickPresets.forEach { (label, presetRect) ->
                        val isClose = kotlin.math.abs(workingRect.left - presetRect.left) < 0.02f &&
                            kotlin.math.abs(workingRect.top - presetRect.top) < 0.02f &&
                            kotlin.math.abs(workingRect.right - presetRect.right) < 0.02f &&
                            kotlin.math.abs(workingRect.bottom - presetRect.bottom) < 0.02f
                        FilterChip(
                            selected = isClose,
                            onClick = { syncInputsFromRect(presetRect) },
                            label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("custom_preset_${label.lowercase().replace(" ", "_")}")
                        )
                    }
                }

                // Interactive Visual Drag Canvas
                InteractiveAreaFramePreview(
                    rect = workingRect,
                    resolution = resolution,
                    onRectChanged = { dragged -> syncInputsFromRect(dragged) },
                    previewWidthDp = 196.dp
                )

                // Direct Numeric Coordinate Inputs (Left %, Top %, Width %, Height %)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AreaNumberField(
                        label = "Left (%)",
                        value = leftInput,
                        onValueChange = { raw ->
                            val clean = raw.filter { it.isDigit() }.take(3)
                            leftInput = clean
                            if (clean.isNotEmpty()) {
                                applyInputsToRect(clean, topInput, widthInput, heightInput)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_area_left_input")
                    )
                    AreaNumberField(
                        label = "Top (%)",
                        value = topInput,
                        onValueChange = { raw ->
                            val clean = raw.filter { it.isDigit() }.take(3)
                            topInput = clean
                            if (clean.isNotEmpty()) {
                                applyInputsToRect(leftInput, clean, widthInput, heightInput)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_area_top_input")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AreaNumberField(
                        label = "Width (%)",
                        value = widthInput,
                        onValueChange = { raw ->
                            val clean = raw.filter { it.isDigit() }.take(3)
                            widthInput = clean
                            if (clean.isNotEmpty()) {
                                applyInputsToRect(leftInput, topInput, clean, heightInput)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_area_width_input")
                    )
                    AreaNumberField(
                        label = "Height (%)",
                        value = heightInput,
                        onValueChange = { raw ->
                            val clean = raw.filter { it.isDigit() }.take(3)
                            heightInput = clean
                            if (clean.isNotEmpty()) {
                                applyInputsToRect(leftInput, topInput, widthInput, clean)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_area_height_input")
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("custom_area_cancel_button")
                    ) {
                        Text(
                            text = stringResource(R.string.action_cancel),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    Button(
                        onClick = {
                            applyInputsToRect(leftInput, topInput, widthInput, heightInput)
                            onApply(workingRect.sanitized())
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp)
                            .testTag("custom_area_apply_button")
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.custom_area_apply),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AreaNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        ),
        modifier = modifier
    )
}
