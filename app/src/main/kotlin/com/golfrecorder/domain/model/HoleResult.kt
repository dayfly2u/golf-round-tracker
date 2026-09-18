package com.golfrecorder.domain.model

data class HoleResult(
    val holeNumber: Int,
    val par: Int,
    val strokesToGreen: Int,
    val strokesGreenToHoleOut: Int,
    /** strokesGreenToHoleOut 중 퍼팅(그린 위) 타수만 따로 — 숏어프로치는
     * strokesGreenToHoleOut - strokesPutt로 구한다. */
    val strokesPutt: Int = 0,
) {
    val totalStrokes: Int get() = strokesToGreen + strokesGreenToHoleOut
    val scoreToPar: Int get() = totalStrokes - par
    val isGreenInRegulation: Boolean get() = strokesToGreen <= par - 2
    val strokesShortGame: Int get() = strokesGreenToHoleOut - strokesPutt
}
