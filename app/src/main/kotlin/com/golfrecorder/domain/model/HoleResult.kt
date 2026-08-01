package com.golfrecorder.domain.model

data class HoleResult(
    val holeNumber: Int,
    val par: Int,
    val strokesToGreen: Int,
    val strokesGreenToHoleOut: Int
) {
    val totalStrokes: Int get() = strokesToGreen + strokesGreenToHoleOut
    val scoreToPar: Int get() = totalStrokes - par
    val isGreenInRegulation: Boolean get() = strokesToGreen <= par - 2
}
