package com.golfrecorder.domain.model

import com.golfrecorder.data.local.entity.PenaltyEntity
import com.golfrecorder.data.local.entity.ShotEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class StrokeCalculatorTest {

    private fun shot(phase: ShotPhase, shotIndex: Int) = ShotEntity(
        roundId = 1, holeNumber = 1, phase = phase.name, shotIndex = shotIndex,
        lat = 0.0, lng = 0.0, capturedAt = 0L,
    )

    private fun penalty(phase: ShotPhase, type: PenaltyType, penaltyIndex: Int, strokeCount: Int) = PenaltyEntity(
        roundId = 1, holeNumber = 1, phase = phase.name, type = type.name,
        penaltyIndex = penaltyIndex, strokeCount = strokeCount, lat = 0.0, lng = 0.0, capturedAt = 0L,
    )

    @Test
    fun `no shots or penalties means zero strokes`() {
        val total = StrokeCalculator.currentTotal(emptyList(), emptyList(), ShotPhase.TO_GREEN)
        assertEquals(0, total)
    }

    @Test
    fun `total is the highest shot index for that phase`() {
        val shots = listOf(shot(ShotPhase.TO_GREEN, 1), shot(ShotPhase.TO_GREEN, 2))
        val total = StrokeCalculator.currentTotal(shots, emptyList(), ShotPhase.TO_GREEN)
        assertEquals(2, total)
    }

    @Test
    fun `penalty with no matching shot still counts toward the total`() {
        // OB 벌타만 있고 실제 샷은 아직 없는 경우 (shots 테이블엔 행이 없음)
        val penalties = listOf(penalty(ShotPhase.TO_GREEN, PenaltyType.OB, penaltyIndex = 2, strokeCount = 2))
        val total = StrokeCalculator.currentTotal(emptyList(), penalties, ShotPhase.TO_GREEN)
        assertEquals(2, total)
    }

    @Test
    fun `total is the max of shot and penalty indices, not their sum`() {
        // 1타 치고(shotIndex=1) OB(penaltyIndex=2)를 선언한 뒤 이어친 상황 등
        val shots = listOf(shot(ShotPhase.TO_GREEN, 1))
        val penalties = listOf(penalty(ShotPhase.TO_GREEN, PenaltyType.OB, penaltyIndex = 2, strokeCount = 1))
        val total = StrokeCalculator.currentTotal(shots, penalties, ShotPhase.TO_GREEN)
        assertEquals(2, total)
    }

    @Test
    fun `phases are independent`() {
        val shots = listOf(shot(ShotPhase.TO_GREEN, 3), shot(ShotPhase.SHORT_GAME, 1))
        val toGreen = StrokeCalculator.currentTotal(shots, emptyList(), ShotPhase.TO_GREEN)
        val shortGame = StrokeCalculator.currentTotal(shots, emptyList(), ShotPhase.SHORT_GAME)
        assertEquals(3, toGreen)
        assertEquals(1, shortGame)
    }
}
