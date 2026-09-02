package com.brave.ytmusic.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brave.ytmusic.equalizer.EqualizerManager
import com.brave.ytmusic.equalizer.EqualizerPreset
import com.brave.ytmusic.ui.theme.AmoledCard
import com.brave.ytmusic.ui.theme.AmoledSurface
import com.brave.ytmusic.ui.theme.TextPrimary
import com.brave.ytmusic.ui.theme.TextSecondary
import com.brave.ytmusic.ui.theme.YtmRed
import com.brave.ytmusic.ui.theme.YtmRedAccent
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerSheet(
    equalizerManager: EqualizerManager,
    onDismissRequest: () -> Unit
) {
    val eqState by equalizerManager.equalizerState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = AmoledSurface,
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .height(4.dp)
                    .fillMaxWidth(0.12f),
                shape = RoundedCornerShape(2.dp),
                color = TextSecondary.copy(alpha = 0.4f)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Studio Equalizer",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                OutlinedButton(
                    onClick = { equalizerManager.resetToFlat() },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = YtmRedAccent
                    )
                ) {
                    Text("Reset", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Presets Horizontal Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EqualizerPreset.values().forEach { preset ->
                    val isSelected = eqState.selectedPresetName == preset.presetName
                    FilterChip(
                        selected = isSelected,
                        onClick = { equalizerManager.applyPreset(preset) },
                        label = { Text(preset.presetName, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = YtmRed,
                            selectedLabelColor = Color.White,
                            containerColor = AmoledCard,
                            labelColor = TextSecondary
                        ),
                        border = null
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 5-Band Sliders
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = AmoledCard
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Frequency Bands (-12 dB to +12 dB)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    eqState.bands.forEach { band ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = band.label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary,
                                modifier = Modifier.width(48.dp)
                            )

                            Slider(
                                value = band.gainDb,
                                onValueChange = { equalizerManager.setBandGain(band.index, it) },
                                valueRange = -12f..12f,
                                steps = 23,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = YtmRed,
                                    activeTrackColor = YtmRed,
                                    inactiveTrackColor = Color(0xFF252525)
                                )
                            )

                            val sign = if (band.gainDb > 0) "+" else ""
                            Text(
                                text = "$sign${band.gainDb.roundToInt()} dB",
                                fontSize = 11.sp,
                                color = if (band.gainDb != 0f) YtmRedAccent else TextSecondary,
                                modifier = Modifier.width(46.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.End
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bass Boost & Preamp Control
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = AmoledCard
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Bass Boost
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Bass Booster", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("+${eqState.bassBoostDb.roundToInt()} dB", fontSize = 12.sp, color = YtmRedAccent)
                    }

                    Slider(
                        value = eqState.bassBoostDb,
                        onValueChange = { equalizerManager.setBassBoost(it) },
                        valueRange = 0f..10f,
                        steps = 9,
                        colors = SliderDefaults.colors(
                            thumbColor = YtmRed,
                            activeTrackColor = YtmRed,
                            inactiveTrackColor = Color(0xFF252525)
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Preamp Gain
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Preamp Gain", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text(String.format(Locale.getDefault(), "%.1fx", eqState.preampGain), fontSize = 12.sp, color = TextSecondary)
                    }

                    Slider(
                        value = eqState.preampGain,
                        onValueChange = { equalizerManager.setPreampGain(it) },
                        valueRange = 0.5f..1.5f,
                        steps = 9,
                        colors = SliderDefaults.colors(
                            thumbColor = YtmRed,
                            activeTrackColor = YtmRed,
                            inactiveTrackColor = Color(0xFF252525)
                        )
                    )
                }
            }
        }
    }
}
