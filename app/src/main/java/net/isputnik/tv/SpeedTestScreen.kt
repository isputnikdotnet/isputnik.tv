package net.isputnik.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import net.isputnik.tv.core.ApiClient
import net.isputnik.tv.core.Destination
import net.isputnik.tv.core.Feature
import net.isputnik.tv.core.Navigator
import net.isputnik.tv.core.ProgressLine
import net.isputnik.tv.core.RestoreFocus
import net.isputnik.tv.core.Route
import net.isputnik.tv.core.SpeedResult
import net.isputnik.tv.core.SpeedVerdict
import net.isputnik.tv.core.TvButton
import net.isputnik.tv.core.TvColors
import net.isputnik.tv.core.formatSpeed
import net.isputnik.tv.core.remembersFocus
import net.isputnik.tv.core.speedVerdict
import net.isputnik.tv.core.testSpeed

internal data object SpeedTestRoute : Route {
    override val key = "speed-test"
}

/** Settings › Test connection speed: how fast this TV pulls from the server, and what that
 *  is enough for. It starts on opening; Back stops it. */
internal class SpeedTestFeature(private val api: ApiClient) : Feature {
    override val destinations = emptyList<Destination>()
    override fun owns(route: Route) = route == SpeedTestRoute

    @Composable
    override fun Content(route: Route, nav: Navigator) {
        var run by remember { mutableIntStateOf(0) }
        var speed by remember { mutableStateOf(0.0) }
        var progress by remember { mutableFloatStateOf(0f) }
        var result by remember { mutableStateOf<SpeedResult?>(null) }
        LaunchedEffect(run) {
            result = null
            speed = 0.0
            progress = 0f
            result = testSpeed(api) { bits, done ->
                speed = bits
                progress = done
            }
        }
        val mbit = stringResource(R.string.speed_mbit)
        val kbit = stringResource(R.string.speed_kbit)

        Column(Modifier.fillMaxSize().padding(56.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.speed_title), color = TvColors.Text, fontSize = 28.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            when (val done = result) {
                null -> {
                    Text(
                        if (speed > 0) formatSpeed(speed, mbit, kbit) else stringResource(R.string.speed_connecting),
                        color = TvColors.Text, fontSize = 44.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    ProgressLine(progress, Modifier.width(420.dp), height = 6.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.speed_running), color = TvColors.Muted, fontSize = 18.sp, textAlign = TextAlign.Center)
                }
                is SpeedResult.Measured -> {
                    Text(formatSpeed(done.bitsPerSecond, mbit, kbit), color = TvColors.Text, fontSize = 44.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(
                            when (speedVerdict(done.bitsPerSecond)) {
                                SpeedVerdict.ORIGINAL_4K -> R.string.speed_verdict_4k
                                SpeedVerdict.P1080 -> R.string.speed_verdict_1080
                                SpeedVerdict.P720 -> R.string.speed_verdict_720
                                SpeedVerdict.P480 -> R.string.speed_verdict_480
                                SpeedVerdict.TOO_SLOW -> R.string.speed_verdict_slow
                            }
                        ),
                        color = TvColors.Text, fontSize = 20.sp, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.speed_note), color = TvColors.Muted, fontSize = 15.sp, textAlign = TextAlign.Center)
                }
                SpeedResult.OldServer -> Text(stringResource(R.string.speed_old_server), color = TvColors.Muted, fontSize = 18.sp, textAlign = TextAlign.Center)
                SpeedResult.Failed -> Text(stringResource(R.string.speed_failed), color = TvColors.Muted, fontSize = 18.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(28.dp))
            if (result != null) {
                TvButton(stringResource(R.string.speed_again), Modifier.remembersFocus("again", isDefault = true), primary = true) { run++ }
            }
        }
        RestoreFocus(result != null)
    }
}
