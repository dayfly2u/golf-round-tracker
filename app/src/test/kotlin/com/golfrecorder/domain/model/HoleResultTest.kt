package com.golfrecorder.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoleResultTest {

    @Test
    fun `totalStrokes sums both segments`() {
        val result = HoleResult(holeNumber = 1, par = 4, strokesToGreen = 2, strokesGreenToHoleOut = 2)
        assertEquals(4, result.totalStrokes)
    }

    @Test
    fun `scoreToPar reports birdie as negative one`() {
        val result = HoleResult(holeNumber = 1, par = 4, strokesToGreen = 2, strokesGreenToHoleOut = 1)
        assertEquals(-1, result.scoreToPar)
    }

    @Test
    fun `scoreToPar reports par as zero`() {
        val result = HoleResult(holeNumber = 1, par = 4, strokesToGreen = 2, strokesGreenToHoleOut = 2)
        assertEquals(0, result.scoreToPar)
    }

    @Test
    fun `scoreToPar reports bogey as positive one`() {
        val result = HoleResult(holeNumber = 1, par = 4, strokesToGreen = 3, strokesGreenToHoleOut = 2)
        assertEquals(1, result.scoreToPar)
    }

    @Test
    fun `par4 is green in regulation when green reached in two strokes`() {
        val result = HoleResult(holeNumber = 1, par = 4, strokesToGreen = 2, strokesGreenToHoleOut = 2)
        assertTrue(result.isGreenInRegulation)
    }

    @Test
    fun `par4 is not green in regulation when green reached in three strokes`() {
        val result = HoleResult(holeNumber = 1, par = 4, strokesToGreen = 3, strokesGreenToHoleOut = 1)
        assertFalse(result.isGreenInRegulation)
    }

    @Test
    fun `par3 requires reaching green in one stroke for regulation`() {
        val onGreen = HoleResult(holeNumber = 1, par = 3, strokesToGreen = 1, strokesGreenToHoleOut = 2)
        val missedGreen = HoleResult(holeNumber = 1, par = 3, strokesToGreen = 2, strokesGreenToHoleOut = 1)
        assertTrue(onGreen.isGreenInRegulation)
        assertFalse(missedGreen.isGreenInRegulation)
    }

    @Test
    fun `par5 allows reaching green in three strokes for regulation`() {
        val result = HoleResult(holeNumber = 1, par = 5, strokesToGreen = 3, strokesGreenToHoleOut = 2)
        assertTrue(result.isGreenInRegulation)
    }
}
