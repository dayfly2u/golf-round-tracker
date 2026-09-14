package com.golfrecorder.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.golfrecorder.domain.model.PenaltyType
import com.golfrecorder.domain.model.ShotPhase
import com.golfrecorder.location.LatLng as AppLatLng
import com.golfrecorder.util.circlePoints
import com.golfrecorder.util.haversineMeters
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolygonOptions
import com.google.android.gms.maps.model.PolylineOptions
import kotlin.math.roundToInt

// CourseMapView.kt와 동일한 색/반경 상수를 쓴다 — 두 SDK의 타입이 달라 그리는 코드
// 자체는 공유하지 않지만, 시각적으로는 카카오 쪽과 똑같이 보여야 하므로 값만 맞춘다.
private const val SHOT_RED = "#E53935"
private const val SHOT_BLUE = "#1E88E5"
private const val SHOT_YELLOW = "#FFEB3B"
private const val GREEN_RADIUS_METERS = 2.0
private const val GREEN_FILL_COLOR = "#CCFFEB3B"
// 카카오 쪽 거리 라벨(28px 텍스트 / 4px 외곽선)과 같은 느낌을 내려고 잡은 값이지만,
// 두 SDK의 좌표/픽셀 단위 체계가 직접 비교 가능한 게 아니라서 이 36/6 값은 눈으로 보고
// 맞춘 근사치일 뿐 픽셀 단위로 검증된 일치는 아니다.
private const val DISTANCE_LABEL_TEXT_SIZE_PX = 36f
private const val DISTANCE_LABEL_STROKE_WIDTH_PX = 6f

private fun ShotPhase.order(): Int = when (this) {
    ShotPhase.TO_GREEN -> 0
    ShotPhase.SHORT_GAME -> 1
    ShotPhase.PUTT -> 2
}

private fun dotIconFor(phase: ShotPhase): Int = when (phase) {
    ShotPhase.TO_GREEN -> com.golfrecorder.R.drawable.ic_dot_red
    ShotPhase.SHORT_GAME -> com.golfrecorder.R.drawable.ic_dot_blue
    ShotPhase.PUTT -> com.golfrecorder.R.drawable.ic_dot_yellow
}

/** 구글맵엔 "지도 좌표에 붙는 텍스트 라벨"이 없어서, 캔버스로 흰 글씨+검은 외곽선
 * 비트맵을 직접 그려 마커 아이콘으로 쓴다 — 카카오 쪽 거리 라벨과 같은 스타일. */
private fun distanceLabelBitmap(text: String): Bitmap {
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = DISTANCE_LABEL_TEXT_SIZE_PX
        textAlign = Paint.Align.CENTER
    }
    val strokePaint = Paint(fillPaint).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = DISTANCE_LABEL_STROKE_WIDTH_PX
    }
    val textWidth = fillPaint.measureText(text)
    val width = (textWidth + DISTANCE_LABEL_STROKE_WIDTH_PX * 2).roundToInt().coerceAtLeast(1)
    val height = (DISTANCE_LABEL_TEXT_SIZE_PX * 1.4f).roundToInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val baselineY = height * 0.75f
    val centerX = width / 2f
    canvas.drawText(text, centerX, baselineY, strokePaint)
    canvas.drawText(text, centerX, baselineY, fillPaint)
    return bitmap
}

internal fun drawOverlaysGoogle(
    map: GoogleMap,
    greenLocation: AppLatLng?,
    shots: List<ShotPoint>,
    penalties: List<PenaltyPoint>,
) {
    map.clear()

    if (greenLocation != null) {
        val circleLatLngs = circlePoints(greenLocation.lat, greenLocation.lng, GREEN_RADIUS_METERS)
            .map { (lat, lng) -> LatLng(lat, lng) }
        map.addPolygon(
            PolygonOptions()
                .addAll(circleLatLngs)
                .fillColor(Color.parseColor(GREEN_FILL_COLOR))
                .strokeWidth(0f)
        )
    }

    val segments = (0 until shots.size - 1)
        .map { i -> shots[i] to shots[i + 1] }
        .filterNot { (from, to) -> to.phase.order() < from.phase.order() }

    segments.forEach { (from, to) ->
        val color = when (from.phase) {
            ShotPhase.TO_GREEN -> Color.parseColor(SHOT_RED)
            ShotPhase.SHORT_GAME -> Color.parseColor(SHOT_BLUE)
            ShotPhase.PUTT -> Color.parseColor(SHOT_YELLOW)
        }
        map.addPolyline(
            PolylineOptions()
                .add(LatLng(from.lat, from.lng), LatLng(to.lat, to.lng))
                .color(color)
                .width(5f)
        )
    }

    val dotDescriptors = mutableMapOf<Int, BitmapDescriptor>()
    fun descriptorFor(drawableRes: Int): BitmapDescriptor =
        dotDescriptors.getOrPut(drawableRes) { BitmapDescriptorFactory.fromResource(drawableRes) }

    shots.forEach { shot ->
        map.addMarker(
            MarkerOptions()
                .position(LatLng(shot.lat, shot.lng))
                .icon(descriptorFor(dotIconFor(shot.phase)))
                .anchor(0.5f, 0.5f)
        )
    }

    segments.forEach { (from, to) ->
        val distanceMeters = haversineMeters(from.lat, from.lng, to.lat, to.lng)
        val midLat = (from.lat + to.lat) / 2
        val midLng = (from.lng + to.lng) / 2
        val bitmap = distanceLabelBitmap("${distanceMeters.roundToInt()}m")
        map.addMarker(
            MarkerOptions()
                .position(LatLng(midLat, midLng))
                .icon(BitmapDescriptorFactory.fromBitmap(bitmap))
                .anchor(0.5f, 0.5f)
        )
    }

    penalties.forEach { penalty ->
        val drawableRes = if (penalty.type == PenaltyType.OB) {
            com.golfrecorder.R.drawable.ic_penalty_ob
        } else {
            com.golfrecorder.R.drawable.ic_penalty_hazard
        }
        map.addMarker(
            MarkerOptions()
                .position(LatLng(penalty.lat, penalty.lng))
                .icon(descriptorFor(drawableRes))
                .anchor(0.5f, 0.5f)
        )
    }
}
