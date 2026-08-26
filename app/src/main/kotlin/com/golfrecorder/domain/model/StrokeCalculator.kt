package com.golfrecorder.domain.model

import com.golfrecorder.data.local.entity.PenaltyEntity
import com.golfrecorder.data.local.entity.ShotEntity

object StrokeCalculator {

    /** [shots]와 [penalties]만 보고 특정 홀·구간(phase)의 "지금 타수"를 복원한다.
     * shotIndex/penaltyIndex 둘 다 "그 시점의 누적 타수"를 담고 있으므로, 둘 중
     * 최댓값이 곧 현재 타수다. */
    fun currentTotal(shots: List<ShotEntity>, penalties: List<PenaltyEntity>, phase: ShotPhase): Int {
        val maxShotIndex = shots.filter { it.phase == phase.name }.maxOfOrNull { it.shotIndex } ?: 0
        val maxPenaltyIndex = penalties.filter { it.phase == phase.name }.maxOfOrNull { it.penaltyIndex } ?: 0
        return maxOf(maxShotIndex, maxPenaltyIndex)
    }
}
