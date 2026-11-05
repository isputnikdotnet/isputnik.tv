package net.isputnik.tv.games

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MovePickerTest {
    private fun c(vararg path: Int, promotion: Char? = null) = Candidate(path.toList(), JsonPrimitive(path.joinToString("-") + (promotion ?: "")), promotion)

    @Test
    fun `a piece then where it goes`() {
        val picker = MovePicker(listOf(c(52, 36), c(52, 44), c(62, 45)))
        assertEquals(setOf(52, 62), picker.movable)
        assertEquals(MovePicker.Result.Nothing, picker.press(36))
        assertEquals(MovePicker.Result.Picked, picker.press(52))
        assertEquals(setOf(36, 44), picker.targets)
        val done = picker.press(36) as MovePicker.Result.Done
        assertEquals(listOf(52, 36), done.moves.single().path)
        assertTrue(picker.chosen.isEmpty())
    }

    @Test
    fun `another piece can be picked instead, and the same one put back`() {
        val picker = MovePicker(listOf(c(52, 36), c(62, 45)))
        picker.press(52)
        picker.press(62)
        assertEquals(setOf(45), picker.targets)
        picker.press(62)
        assertTrue(picker.chosen.isEmpty())
    }

    @Test
    fun `a capture in two jumps is walked landing by landing`() {
        val picker = MovePicker(listOf(c(40, 26, 12), c(40, 26, 8), c(42, 33)))
        picker.press(40)
        assertEquals(setOf(26), picker.targets)
        assertEquals(MovePicker.Result.Picked, picker.press(26))
        assertEquals(setOf(12, 8), picker.targets)
        val done = picker.press(8) as MovePicker.Result.Done
        assertEquals(listOf(40, 26, 8), done.moves.single().path)
    }

    @Test
    fun `a promotion leaves the piece to choose`() {
        val picker = MovePicker(listOf(c(8, 0, promotion = 'q'), c(8, 0, promotion = 'n'), c(8, 0, promotion = 'r'), c(8, 0, promotion = 'b')))
        picker.press(8)
        val done = picker.press(0) as MovePicker.Result.Done
        assertEquals(4, done.moves.size)
    }

    @Test
    fun `squares and cells`() {
        assertEquals(56, cellOfSquare("a1"))
        assertEquals(7, cellOfSquare("h8"))
        assertEquals("e4", squareOfCell(cellOfSquare("e4")))
    }
}
