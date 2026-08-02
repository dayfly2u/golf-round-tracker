package com.golfrecorder.ui.map

import android.graphics.Color
import com.golfrecorder.R
import com.golfrecorder.domain.model.PenaltyType
import com.golfrecorder.domain.model.ShotPhase
import com.golfrecorder.location.LatLng as AppLatLng
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.shape.MapPoints
import com.kakao.vectormap.shape.PolylineOptions
import com.kakao.vectormap.shape.PolylineStyle
import com.kakao.vectormap.shape.ShapeLayerOptions
import com.kakao.vectormap.shape.ShapeLayerPass

data class ShotPoint(val phase: ShotPhase, val lat: Double, val lng: Double)

/** OB/해저드는 순수 벌타라 실제 '샷'이 아니다 — 선으로 연결하지 않고 위치만 표시한다. */
data class PenaltyPoint(val type: PenaltyType, val lat: Double, val lng: Double)

// ic_dot_red.png / ic_dot_blue.png와 동일한 색상 (선 색을 원 색상과 맞추기 위함).
private const val SHOT_RED = "#E53935"
private const val SHOT_BLUE = "#1E88E5"

// 골프 한 홀(티~그린) 정도가 화면에 들어오는 정도로 시작 — 값이 높을수록 확대됨.
internal const val MAP_ZOOM_LEVEL = 16

private const val SHOT_LINE_LAYER_ID = "shot-lines"

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
    if (shots.size >= 2) {
        val toGreenLineStyle = PolylineStyle.from(5f, Color.parseColor(SHOT_RED))
        val shortGameLineStyle = PolylineStyle.from(5f, Color.parseColor(SHOT_BLUE))
        for (i in 0 until shots.size - 1) {
            val from = shots[i]
            val to = shots[i + 1]
            // 선 색은 출발점(이전 샷)의 원 색상과 동일하게 맞춘다.
            val segmentStyle = if (from.phase == ShotPhase.TO_GREEN) toGreenLineStyle else shortGameLineStyle
            val segmentPoints = MapPoints.fromLatLng(
                listOf(LatLng.from(from.lat, from.lng), LatLng.from(to.lat, to.lng))
            )
            shapeLayer?.addPolyline(PolylineOptions.from(segmentPoints, segmentStyle))
        }
    }

    labelLayer.removeAll()

    if (greenLocation != null) {
        // 아이콘이 핀이 아니라 원형이라 실제 좌표는 원의 중심이어야 한다.
        // 기본 anchor(0.5, 1.0=하단 중심)를 쓰면 좌표가 원 아래쪽 끝에 고정되어
        // 탭한 위치보다 위로 떠 보인다.
        val greenStyles = map.labelManager?.addLabelStyles(
            LabelStyles.from("green-pin", LabelStyle.from(R.drawable.ic_green_pin).setAnchorPoint(0.5f, 0.5f))
        )
        labelLayer.addLabel(
            LabelOptions.from(LatLng.from(greenLocation.lat, greenLocation.lng)).setStyles(greenStyles)
        )
    }

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
