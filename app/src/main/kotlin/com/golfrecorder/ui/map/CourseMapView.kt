package com.golfrecorder.ui.map

import android.graphics.Color
import com.golfrecorder.R
import com.golfrecorder.domain.model.PenaltyType
import com.golfrecorder.domain.model.ShotPhase
import com.golfrecorder.location.LatLng as AppLatLng
import com.golfrecorder.util.circlePoints
import com.golfrecorder.util.haversineMeters
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.label.LabelTextBuilder
import com.kakao.vectormap.label.LabelTextStyle
import com.kakao.vectormap.shape.MapPoints
import com.kakao.vectormap.shape.PolygonOptions
import com.kakao.vectormap.shape.PolylineOptions
import com.kakao.vectormap.shape.PolylineStyle
import com.kakao.vectormap.shape.ShapeLayerOptions
import com.kakao.vectormap.shape.ShapeLayerPass
import kotlin.math.roundToInt

data class ShotPoint(val phase: ShotPhase, val lat: Double, val lng: Double)

/** OB/해저드는 순수 벌타라 실제 '샷'이 아니다 — 선으로 연결하지 않고 위치만 표시한다. */
data class PenaltyPoint(val type: PenaltyType, val lat: Double, val lng: Double)

// ic_dot_red.png / ic_dot_blue.png와 동일한 색상 (선 색을 원 색상과 맞추기 위함).
private const val SHOT_RED = "#E53935"
private const val SHOT_BLUE = "#1E88E5"

// 필드 테스트 결과 600m(레벨 18)로는 홀 전체가 화면에 안 들어오는 경우가 많아
// 가로세로 약 1200m가 보이도록 한 단계 축소함 — 값이 높을수록 확대됨. 표준 웹
// 메르카토르 배율(위도 37°N, 화면 가로 1080px 기준)로 역산하면 레벨 16은 화면
// 가로가 약 2000m, 18이 약 500~550m라 그 중간인 17이 1200m 목표에 가장 가깝다.
// SDK가 이 배율을 그대로 따르는지는 문서로 확인이 안 돼서 추정치 — 실제로 봤을
// 때 다르면 조정 필요.
internal const val MAP_ZOOM_LEVEL = 17

private const val SHOT_LINE_LAYER_ID = "shot-lines"

// 그린 전체가 아니라 핀(홀)의 정확한 위치를 표시하는 작은 원.
private const val GREEN_RADIUS_METERS = 2.0
// 위성사진 자체에 초록(잔디)이 많아 녹색으로는 잘 안 보여서 노란색으로 표시한다.
private const val GREEN_FILL_COLOR = "#CCFFEB3B" // 노랑, ARGB 약 80% 불투명도

// 샷 구간 거리 라벨 — 위성사진 위에서 잘 보이도록 흰 글씨에 검은 외곽선을 준다.
private const val DISTANCE_LABEL_TEXT_SIZE = 28
private const val DISTANCE_LABEL_STROKE_WIDTH = 4

internal fun drawOverlays(
    map: KakaoMap,
    greenLocation: AppLatLng?,
    shots: List<ShotPoint>,
    penalties: List<PenaltyPoint>,
) {
    val labelLayer = map.labelManager?.layer ?: return

    // 기본 shape 레이어(ShapeLayerPass.Default)는 "지도와 배경 사이"에 그려져서
    // 위성 타일 밑에 깔려 안 보인다 — Overlay 패스로 명시적인 레이어를 만들어야
    // 실제로 화면에 보인다. 지도는 앱 내내 재사용되므로 이미 만들어 둔 레이어가
    // 있으면 그대로 쓴다.
    val shapeManager = map.shapeManager
    val shapeLayer = shapeManager?.getLayer(SHOT_LINE_LAYER_ID)
        ?: shapeManager?.addLayer(
            ShapeLayerOptions.from(SHOT_LINE_LAYER_ID, 10001, ShapeLayerPass.Overlay)
        )
    shapeLayer?.removeAll()
    if (greenLocation != null) {
        // Kakao의 DotPoints.fromCircle은 반지름 단위가 명확하지 않고(문서상 "px"),
        // 실제로 그려보면 화면에 전혀 안 보이는 문제가 있었다. 대신 실제 위경도로
        // 원 둘레 좌표를 직접 계산해서 폴리곤을 그린다 — 지리 좌표 기반이라
        // 확대/축소하면 화면 크기도 실제 거리 비율대로 같이 변한다.
        val circleLatLngs = circlePoints(greenLocation.lat, greenLocation.lng, GREEN_RADIUS_METERS)
            .map { (lat, lng) -> LatLng.from(lat, lng) }
        shapeLayer?.addPolygon(
            PolygonOptions.from(MapPoints.fromLatLng(circleLatLngs), Color.parseColor(GREEN_FILL_COLOR))
        )
    }
    // 빨강(그린까지)에서 파랑(숏게임/퍼팅)으로 이어지는 선은 어프로치 후 그린
    // 주변으로 넘어가는 정상적인 연결이라 그린다. 하지만 그 반대 — 파랑에서
    // 빨강으로 되돌아가는 선 — 은 절대 있을 수 없는 순서다(정상 데이터는 항상
    // TO_GREEN 구간이 먼저, SHORT_GAME 구간이 나중). 홀 전환 중 위치 기록 경합
    // 등으로 리스트에 이런 순서가 섞여 들어오더라도 지도에는 절대 그리지 않는다.
    val segments = (0 until shots.size - 1)
        .map { i -> shots[i] to shots[i + 1] }
        .filterNot { (from, to) -> from.phase == ShotPhase.SHORT_GAME && to.phase == ShotPhase.TO_GREEN }

    if (segments.isNotEmpty()) {
        val toGreenLineStyle = PolylineStyle.from(5f, Color.parseColor(SHOT_RED))
        val shortGameLineStyle = PolylineStyle.from(5f, Color.parseColor(SHOT_BLUE))
        segments.forEach { (from, to) ->
            val segmentStyle = if (from.phase == ShotPhase.TO_GREEN) toGreenLineStyle else shortGameLineStyle
            val segmentPoints = MapPoints.fromLatLng(
                listOf(LatLng.from(from.lat, from.lng), LatLng.from(to.lat, to.lng))
            )
            shapeLayer?.addPolyline(PolylineOptions.from(segmentPoints, segmentStyle))
        }
    }

    labelLayer.removeAll()

    val toGreenStyles = map.labelManager?.addLabelStyles(
        LabelStyles.from("shot-to-green", LabelStyle.from(R.drawable.ic_dot_red).setAnchorPoint(0.5f, 0.5f))
    )
    val shortGameStyles = map.labelManager?.addLabelStyles(
        LabelStyles.from("shot-short-game", LabelStyle.from(R.drawable.ic_dot_blue).setAnchorPoint(0.5f, 0.5f))
    )
    shots.forEach { shot ->
        val styles = if (shot.phase == ShotPhase.TO_GREEN) toGreenStyles else shortGameStyles
        labelLayer.addLabel(LabelOptions.from(LatLng.from(shot.lat, shot.lng)).setStyles(styles))
    }

    if (segments.isNotEmpty()) {
        val distanceLabelStyles = map.labelManager?.addLabelStyles(
            LabelStyles.from(
                "shot-distance",
                LabelStyle.from(
                    LabelTextStyle.from(DISTANCE_LABEL_TEXT_SIZE, Color.WHITE, DISTANCE_LABEL_STROKE_WIDTH, Color.BLACK)
                )
            )
        )
        segments.forEach { (from, to) ->
            val distanceMeters = haversineMeters(from.lat, from.lng, to.lat, to.lng)
            val midLat = (from.lat + to.lat) / 2
            val midLng = (from.lng + to.lng) / 2
            labelLayer.addLabel(
                LabelOptions.from(LatLng.from(midLat, midLng))
                    .setStyles(distanceLabelStyles)
                    .setTexts(LabelTextBuilder().setTexts("${distanceMeters.roundToInt()}m"))
            )
        }
    }

    val obStyles = map.labelManager?.addLabelStyles(
        LabelStyles.from("penalty-ob", LabelStyle.from(R.drawable.ic_penalty_ob).setAnchorPoint(0.5f, 0.5f))
    )
    val hazardStyles = map.labelManager?.addLabelStyles(
        LabelStyles.from("penalty-hazard", LabelStyle.from(R.drawable.ic_penalty_hazard).setAnchorPoint(0.5f, 0.5f))
    )
    penalties.forEach { penalty ->
        val styles = if (penalty.type == PenaltyType.OB) obStyles else hazardStyles
        labelLayer.addLabel(LabelOptions.from(LatLng.from(penalty.lat, penalty.lng)).setStyles(styles))
    }
}
