package com.golfrecorder.ui.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import com.golfrecorder.data.local.entity.ShotEntity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 숏게임+퍼팅 구간의 샷이 그린 위(퍼팅)인지 그린 밖(칩/피치)인지를 위성사진 색으로
 * 추정한다. 카카오맵 SDK는 렌더링된 특정 좌표의 픽셀 색을 읽어오는 API가 없어서,
 * 대신 무료 공개 위성 타일(Esri World Imagery)을 별도로 받아 좌표별 픽셀 색을 직접
 * 샘플링한다 — 화면에 실제로 보여주는 지도(카카오)와는 완전히 무관한, 순수 색 판별용
 * 백그라운드 조회다.
 *
 * 판별 방식: 그 홀의 "그린까지" 구간 샷들(정의상 아직 그린에 도달하지 못한 위치)의
 * 밝기를 그 홀의 "그린 밖" 기준값으로 삼고, 숏게임 샷의 밝기가 그 기준보다 일정
 * 이상 밝으면(그린은 잔디가 짧고 균일해서 대체로 더 밝고 매끈하게 찍힌다) 그린 위로
 * 판정한다. 코스/조명마다 절대 색이 달라서 고정 임계값이 아니라 같은 홀 안에서의
 * 상대 비교를 쓴다. 실제 라운드 데이터(round 75, 1홀)로 확인된 값: TO_GREEN 밝기
 * 평균 ~260(0~765 스케일), 그린 밖 숏게임 샷 ~154, 그린 위 숏게임 샷 ~315.
 */
object GreenClassifier {
    private const val TILE_SIZE = 256
    private const val ZOOM = 18
    private const val TILE_URL_TEMPLATE =
        "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/%d/%d/%d"

    // 0~765(RGB 합) 스케일에서 TO_GREEN 기준보다 이만큼 더 밝아야 "그린 위"로 판정한다.
    // 실측 사례(위 문서 참고) 기준으로 잡은 시작값 — 실제 여러 코스에서 써보면서
    // 조정이 필요할 수 있다.
    private const val BRIGHTNESS_MARGIN = 25

    // 샷 좌표들을 둘러싼 타일을 받아올 때 둘 여백(위경도 도 단위, 대략 60~70m).
    private const val PADDING_DEGREES = 0.0006

    private val client = OkHttpClient()

    /**
     * [toGreenShots]와 [shortGameShots]는 같은 홀의 shots를 phase로 미리 나눠서 넘긴다.
     * 반환값은 shotIndex -> 그린 위 여부. 네트워크 실패나 판별 불가 시 빈 맵을
     * 반환한다 — 호출자는 이 경우 기존 파란 점을 그대로 둔다(사용자에게 실패를
     * 알리는 것은 호출부 책임).
     */
    suspend fun classifyShortGameShots(
        toGreenShots: List<ShotEntity>,
        shortGameShots: List<ShotEntity>,
    ): Map<Int, Boolean> = withContext(Dispatchers.IO) {
        if (shortGameShots.isEmpty()) return@withContext emptyMap()
        runCatching {
            val allLats = (toGreenShots.map { it.lat } + shortGameShots.map { it.lat })
            val allLngs = (toGreenShots.map { it.lng } + shortGameShots.map { it.lng })
            val minLat = allLats.min() - PADDING_DEGREES
            val maxLat = allLats.max() + PADDING_DEGREES
            val minLng = allLngs.min() - PADDING_DEGREES
            val maxLng = allLngs.max() + PADDING_DEGREES

            val stitched = fetchStitchedTiles(minLat, maxLat, minLng, maxLng)

            fun brightnessAt(lat: Double, lng: Double): Int? {
                val (px, py) = latLngToPixel(lat, lng)
                val x = (px - stitched.originX).toInt()
                val y = (py - stitched.originY).toInt()
                if (x < 2 || y < 2 || x >= stitched.bitmap.width - 2 || y >= stitched.bitmap.height - 2) return null
                var sum = 0
                var count = 0
                for (dx in -2..2) {
                    for (dy in -2..2) {
                        val pixel = stitched.bitmap.getPixel(x + dx, y + dy)
                        sum += Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)
                        count++
                    }
                }
                return sum / count
            }

            val baselineSamples = toGreenShots.mapNotNull { brightnessAt(it.lat, it.lng) }
            if (baselineSamples.isEmpty()) return@runCatching emptyMap()
            val baseline = baselineSamples.average()

            shortGameShots.mapNotNull { shot ->
                val brightness = brightnessAt(shot.lat, shot.lng) ?: return@mapNotNull null
                shot.shotIndex to (brightness > baseline + BRIGHTNESS_MARGIN)
            }.toMap()
        }.getOrElse { emptyMap() }
    }

    private fun latLngToPixel(lat: Double, lng: Double): Pair<Double, Double> {
        val latRad = Math.toRadians(lat)
        val n = Math.pow(2.0, ZOOM.toDouble())
        val x = (lng + 180.0) / 360.0 * n * TILE_SIZE
        val y = (1.0 - ln(tan(latRad) + 1 / cos(latRad)) / PI) / 2.0 * n * TILE_SIZE
        return x to y
    }

    private class StitchedTiles(val originX: Double, val originY: Double, val bitmap: Bitmap)

    private fun fetchStitchedTiles(minLat: Double, maxLat: Double, minLng: Double, maxLng: Double): StitchedTiles {
        val (x0, y0) = latLngToPixel(maxLat, minLng) // top-left: max lat = smaller pixel y
        val (x1, y1) = latLngToPixel(minLat, maxLng) // bottom-right
        val tileX0 = floor(x0 / TILE_SIZE).toInt()
        val tileY0 = floor(y0 / TILE_SIZE).toInt()
        val tileX1 = floor(x1 / TILE_SIZE).toInt()
        val tileY1 = floor(y1 / TILE_SIZE).toInt()

        val tilesWide = tileX1 - tileX0 + 1
        val tilesHigh = tileY1 - tileY0 + 1
        val canvasBitmap = Bitmap.createBitmap(tilesWide * TILE_SIZE, tilesHigh * TILE_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBitmap)

        for (ty in tileY0..tileY1) {
            for (tx in tileX0..tileX1) {
                val url = String.format(TILE_URL_TEMPLATE, ZOOM, ty, tx)
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    val bytes = response.body?.bytes() ?: return@use
                    val tileBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@use
                    canvas.drawBitmap(
                        tileBitmap,
                        ((tx - tileX0) * TILE_SIZE).toFloat(),
                        ((ty - tileY0) * TILE_SIZE).toFloat(),
                        null,
                    )
                }
            }
        }
        return StitchedTiles((tileX0 * TILE_SIZE).toDouble(), (tileY0 * TILE_SIZE).toDouble(), canvasBitmap)
    }
}
