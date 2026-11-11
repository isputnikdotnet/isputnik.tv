package net.isputnik.tv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QualityTest {
    @Test fun namesCommonSizes() {
        assertEquals("4K", resolutionLabel(3840, 2160))
        assertEquals("4K", resolutionLabel(3840, 1600)) // scope, cropped
        assertEquals("1440p", resolutionLabel(2560, 1440))
        assertEquals("1080p", resolutionLabel(1920, 1080))
        assertEquals("1080p", resolutionLabel(1920, 800))
        assertEquals("1080p", resolutionLabel(1440, 1080)) // 4:3 HD
        assertEquals("720p", resolutionLabel(1280, 720))
        assertEquals("720p", resolutionLabel(1280, 536))
        assertEquals("576p", resolutionLabel(720, 576)) // PAL DVD
        assertEquals("480p", resolutionLabel(720, 480)) // NTSC DVD
        assertEquals("480p", resolutionLabel(854, 356))
        assertEquals("360p", resolutionLabel(480, 360))
        assertNull(resolutionLabel(0, 0))
    }

    @Test fun upscalesSmallerPictures() {
        assertEquals(Scaling.UPSCALE, scaling(1280, 720, 1f, 1920, 1080))
        assertEquals(Scaling.UPSCALE, scaling(1920, 1080, 1f, 3840, 2160))
        // A PAL DVD, 16:9 anamorphic: 720 wide stored, 1024 shown.
        assertEquals(Scaling.UPSCALE, scaling(720, 576, 64f / 45f, 1920, 1080))
    }

    @Test fun downscalesBiggerPictures() {
        assertEquals(Scaling.DOWNSCALE, scaling(3840, 2160, 1f, 1920, 1080))
        assertEquals(Scaling.DOWNSCALE, scaling(3840, 1600, 1f, 1920, 1080))
    }

    @Test fun sameSizeIsNative() {
        assertEquals(Scaling.NATIVE, scaling(1920, 1080, 1f, 1920, 1080))
        assertEquals(Scaling.NATIVE, scaling(1920, 800, 1f, 1920, 1080)) // letterboxed, width fits
        assertEquals(Scaling.NATIVE, scaling(1904, 1072, 1f, 1920, 1080))
        assertNull(scaling(1920, 1080, 1f, 0, 0))
    }

    @Test fun namesCodecs() {
        assertEquals("H.264", codecName("video/avc"))
        assertEquals("HEVC", codecName("video/hevc"))
        assertEquals("AAC", codecName("audio/mp4a-latm"))
        assertEquals("Dolby Digital", codecName("audio/ac3"))
        assertEquals("MATROSKA", codecName("video/x-matroska"))
        assertNull(codecName(null))
    }

    @Test fun saysWhichCopy() {
        assertEquals(Copy.Original, copyOf(false, "transcode", 720))
        assertEquals(Copy.Repacked, copyOf(true, "remux", null))
        assertEquals(Copy.Converted(720), copyOf(true, "transcode", 720))
        assertEquals(Copy.Converted(null), copyOf(true, "transcode", null))
    }
}
