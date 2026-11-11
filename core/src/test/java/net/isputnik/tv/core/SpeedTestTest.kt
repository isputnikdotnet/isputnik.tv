package net.isputnik.tv.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedTestTest {
    @Test fun formatsSpeeds() {
        assertEquals(42_000_000.0, bitsPerSecond(5_250_000, 1_000_000_000), 0.001)
        assertEquals(0.0, bitsPerSecond(100, 0), 0.0)
        assertEquals("42 Mbit/s", formatSpeed(42_400_000.0))
        assertEquals("8.5 Mbit/s", formatSpeed(8_460_000.0))
        assertEquals("8 Mbit/s", formatSpeed(8_010_000.0))
        assertEquals("10 Mbit/s", formatSpeed(9_990_000.0))
        assertEquals("850 kbit/s", formatSpeed(850_000.0))
        assertEquals("850 кбит/с", formatSpeed(850_000.0, "Мбит/с", "кбит/с"))
    }

    @Test fun saysWhatASpeedCarries() {
        assertEquals(SpeedVerdict.ORIGINAL_4K, speedVerdict(90e6))
        assertEquals(SpeedVerdict.P1080, speedVerdict(42e6))
        assertEquals(SpeedVerdict.P720, speedVerdict(8e6))
        assertEquals(SpeedVerdict.P480, speedVerdict(3.5e6))
        assertEquals(SpeedVerdict.TOO_SLOW, speedVerdict(1e6))
    }

    @Test fun meterLeavesOutTheWarmUp() {
        val meter = SpeedMeter(warmUpBytes = 1000, limitNanos = 2_000_000_000)
        // A slow first stretch (connecting) is not counted.
        meter.add(1000, 500_000_000)
        assertEquals(0.0, meter.bitsPerSecond, 0.0)
        assertFalse(meter.done(500_000_000))
        // Then 1,250,000 bytes in one second: 10 Mbit/s.
        meter.add(625_000, 1_000_000_000)
        meter.add(625_000, 1_500_000_000)
        assertEquals(10_000_000.0, meter.bitsPerSecond, 1.0)
        assertEquals(0.5f, meter.progress(1_500_000_000), 0.001f)
        assertFalse(meter.done(2_000_000_000))
        assertTrue(meter.done(2_500_000_000))
    }
}
