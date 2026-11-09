package net.isputnik.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import net.isputnik.tv.core.ApiException
import net.isputnik.tv.core.TvButton
import net.isputnik.tv.core.TvColors
import net.isputnik.tv.core.requestWhenReady

/** What leaving a locked profile is for, so the pad can say so. */
internal enum class PinPurpose { Profile, Person, Add, Unlink }

private const val PIN_MAX = 8
private const val PIN_MIN = 4

private val DIGIT_KEYS = mapOf(
    Key.Zero to '0', Key.One to '1', Key.Two to '2', Key.Three to '3', Key.Four to '4',
    Key.Five to '5', Key.Six to '6', Key.Seven to '7', Key.Eight to '8', Key.Nine to '9',
    Key.NumPad0 to '0', Key.NumPad1 to '1', Key.NumPad2 to '2', Key.NumPad3 to '3', Key.NumPad4 to '4',
    Key.NumPad5 to '5', Key.NumPad6 to '6', Key.NumPad7 to '7', Key.NumPad8 to '8', Key.NumPad9 to '9'
)

/**
 * The PIN pad that guards leaving a restricted profile (home's viewing profiles, phase P2).
 * Digits by D-pad on the on-screen pad, or straight from a remote that has number keys.
 * [submit] asks home; it throws an [ApiException] for a wrong or locked-out PIN, which the
 * pad shows, and returns normally when the PIN was right.
 */
@Composable
internal fun PinScreen(purpose: PinPurpose, submit: suspend (String) -> Unit, onCancel: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestWhenReady() }
    BackHandler { onCancel() }

    val wrong = stringResource(R.string.pin_wrong)
    val locked = stringResource(R.string.pin_locked)
    val failed = stringResource(R.string.pin_failed)
    val triesFormat = stringResource(R.string.pin_tries_left)

    fun type(digit: Char) {
        if (busy || pin.length >= PIN_MAX) return
        pin += digit
        error = null
    }
    fun erase() {
        if (!busy) pin = pin.dropLast(1)
    }
    fun enter() {
        if (busy || pin.length < PIN_MIN) return
        busy = true
        scope.launch {
            try {
                submit(pin)
            } catch (e: ApiException) {
                error = when (e.reason) {
                    "pin_locked" -> locked
                    "pin_wrong" -> e.triesLeft?.let { "$wrong ${triesFormat.format(it)}" } ?: wrong
                    else -> e.message ?: failed
                }
                pin = ""
            } catch (_: Exception) {
                error = failed
            } finally {
                busy = false
            }
        }
    }

    Centered {
        Column(
            Modifier.onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                DIGIT_KEYS[event.key]?.let { type(it); true } ?: when (event.key) {
                    Key.Backspace, Key.Delete -> { erase(); true }
                    else -> false
                }
            },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.pin_title), color = TvColors.Text, fontSize = 28.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(
                    when (purpose) {
                        PinPurpose.Profile -> R.string.pin_why_profile
                        PinPurpose.Person -> R.string.pin_why_person
                        PinPurpose.Add -> R.string.pin_why_add
                        PinPurpose.Unlink -> R.string.pin_why_unlink
                    }
                ),
                color = TvColors.Muted,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(20.dp))
            // One dot per digit typed; never the digits themselves.
            Text(if (pin.isEmpty()) "–" else "●".repeat(pin.length), color = TvColors.Text, fontSize = 34.sp, letterSpacing = 6.sp)
            Spacer(Modifier.height(10.dp))
            Text(error ?: " ", color = TvColors.Accent, fontSize = 16.sp)
            Spacer(Modifier.height(16.dp))
            val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
            rows.forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEachIndexed { c, digit ->
                        TvButton(digit, Modifier.width(72.dp).let { if (r == 0 && c == 0) it.focusRequester(first) else it }) { type(digit[0]) }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvButton(stringResource(R.string.pin_erase), Modifier.width(72.dp)) { erase() }
                TvButton("0", Modifier.width(72.dp)) { type('0') }
                TvButton(stringResource(R.string.pin_ok), Modifier.width(72.dp), primary = pin.length >= PIN_MIN) { enter() }
            }
            Spacer(Modifier.height(18.dp))
            TvButton(stringResource(R.string.pin_cancel)) { onCancel() }
        }
    }
}
