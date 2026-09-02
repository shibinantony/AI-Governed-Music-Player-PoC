package com.brave.ytmusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brave.ytmusic.timer.SleepTimerManager
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
fun SleepTimerSheet(
    sleepTimerManager: SleepTimerManager,
    onDismissRequest: () -> Unit
) {
    val timerState by sleepTimerManager.timerState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var customMinutes by remember { mutableFloatStateOf(30f) }

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
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Sleep Timer",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Active Countdown Display
            if (timerState.isActive) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = AmoledCard
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val minutes = timerState.remainingSeconds / 60
                        val seconds = timerState.remainingSeconds % 60
                        val timeStr = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)

                        Text(
                            text = "Stopping in $timeStr",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (timerState.isFadingOut) YtmRedAccent else YtmRed
                        )

                        if (timerState.isFadingOut) {
                            Text(
                                text = "Exponential audio fade-out active...",
                                fontSize = 12.sp,
                                color = YtmRedAccent,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = { sleepTimerManager.cancelTimer() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Text("Turn Off Timer")
                        }
                    }
                }
            } else {
                Text(
                    text = "Automatically pause music and preserve battery",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Presets
            Text(
                text = "Presets",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(15, 30, 45, 60).forEach { mins ->
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = AmoledCard,
                            contentColor = TextPrimary
                        ),
                        onClick = {
                            sleepTimerManager.startTimer(mins)
                            onDismissRequest()
                        }
                    ) {
                        Text("${mins}m", fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Custom Slider
            Text(
                text = "Custom: ${customMinutes.roundToInt()} minutes",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.align(Alignment.Start)
            )

            Slider(
                value = customMinutes,
                onValueChange = { customMinutes = it },
                valueRange = 5f..120f,
                steps = 22,
                colors = SliderDefaults.colors(
                    thumbColor = YtmRed,
                    activeTrackColor = YtmRed,
                    inactiveTrackColor = AmoledCard
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = YtmRed,
                    contentColor = TextPrimary
                ),
                onClick = {
                    sleepTimerManager.startTimer(customMinutes.roundToInt())
                    onDismissRequest()
                }
            ) {
                Text("Start ${customMinutes.roundToInt()} Min Timer", fontWeight = FontWeight.Bold)
            }
        }
    }
}
