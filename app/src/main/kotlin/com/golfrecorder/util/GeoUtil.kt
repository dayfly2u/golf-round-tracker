package com.golfrecorder.util

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_METERS = 6_371_000.0

/** 두 좌표 사이의 하버사인 거리(미터)를 계산한다. */
fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return EARTH_RADIUS_METERS * c
}

/** (lat1,lng1)에서 (lat2,lng2)를 바라보는 초기 방위각(도, 북쪽 0도 기준 시계방향). */
fun bearingDegrees(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val deltaLambda = Math.toRadians(lng2 - lng1)
    val y = sin(deltaLambda) * cos(phi2)
    val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
    val theta = atan2(y, x)
    return (Math.toDegrees(theta) + 360) % 360
}

/** (lat,lng)에서 [bearingDeg] 방향으로 [distanceMeters]만큼 떨어진 좌표. */
fun destinationPoint(lat: Double, lng: Double, bearingDeg: Double, distanceMeters: Double): Pair<Double, Double> {
    val phi1 = Math.toRadians(lat)
    val lambda1 = Math.toRadians(lng)
    val theta = Math.toRadians(bearingDeg)
    val delta = distanceMeters / EARTH_RADIUS_METERS

    val phi2 = asin(sin(phi1) * cos(delta) + cos(phi1) * sin(delta) * cos(theta))
    val lambda2 = lambda1 + atan2(
        sin(theta) * sin(delta) * cos(phi1),
        cos(delta) - sin(phi1) * sin(phi2)
    )
    return Math.toDegrees(phi2) to Math.toDegrees(lambda2)
}

/**
 * 중심 좌표를 기준으로 반경 [radiusMeters]짜리 원을 이루는 [segments]개의 좌표를 만든다.
 * 지도 SDK의 화면 픽셀 기반 원 API에 기대는 대신, 실제 지리 좌표로 원을 그려서
 * 확대/축소할 때 화면 크기가 실제 거리 비율대로 같이 변하게 한다.
 */
fun circlePoints(lat: Double, lng: Double, radiusMeters: Double, segments: Int = 36): List<Pair<Double, Double>> =
    (0 until segments).map { i ->
        val bearing = 360.0 * i / segments
        destinationPoint(lat, lng, bearing, radiusMeters)
    }
