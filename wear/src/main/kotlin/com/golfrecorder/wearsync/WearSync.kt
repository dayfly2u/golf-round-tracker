package com.golfrecorder.wearsync

/**
 * 폰 앱(:app)과 워치 앱(:wear)이 Data Layer로 주고받는 경로/키 상수.
 * 두 모듈은 별도 Gradle 모듈이라 소스를 공유하지 않으므로, 이 파일은
 * `wear/src/main/kotlin/com/golfrecorder/wearsync/WearSync.kt`에 내용을
 * 그대로 복제해서 둔다 — 한쪽만 고치면 통신이 깨지니 항상 같이 수정할 것.
 */
object WearSync {
    /** 워치 → 폰: 버튼 입력 명령. MessageClient, payload는 UTF-8 문자열이며 형식은
     * "ACTION" 또는 "ACTION|위도|경도" 두 가지다 — 이 라운드가 워치 GPS를 위치
     * 기준으로 쓸 때만(KEY_USE_WATCH_LOCATION=true) INCREMENT_* 액션에 그 순간
     * 워치가 갖고 있던 좌표를 붙여 보낸다. DECREMENT_* 액션이나 홀 이동 액션은
     * 위치가 필요 없어 좌표를 붙이지 않는다. */
    const val ACTION_PATH = "/stroke/action"

    /** 폰 → 워치: 최신 라운드 상태. DataClient DataMap. */
    const val STATE_PATH = "/round-state"

    const val ACTION_INCREMENT_TO_GREEN = "INCREMENT_TO_GREEN"
    const val ACTION_DECREMENT_TO_GREEN = "DECREMENT_TO_GREEN"
    // 워치는 숏어프로치(칩)는 다루지 않고 퍼팅만 올린다 — 폰에서는 숏어프로치/퍼팅을
    // 따로 입력할 수 있지만, 워치 화면은 작아서 퍼팅 하나만 남겼다.
    const val ACTION_INCREMENT_PUTT = "INCREMENT_PUTT"
    const val ACTION_DECREMENT_PUTT = "DECREMENT_PUTT"
    const val ACTION_NEXT_HOLE = "NEXT_HOLE"
    const val ACTION_PREV_HOLE = "PREV_HOLE"

    const val KEY_ROUND_ACTIVE = "roundActive"
    const val KEY_HOLE_NUMBER = "holeNumber"
    const val KEY_PAR = "par"
    const val KEY_STROKES_TO_GREEN = "strokesToGreen"
    const val KEY_STROKES_PUTT = "strokesPutt"
    const val KEY_HOLE_COUNT = "holeCount"
    /** DataItem은 내용이 바뀌지 않으면 재전송해도 워치의 onDataChanged가 다시 호출되지
     * 않는다(내용 해시 기준 중복 제거) — 값이 우연히 이전과 같아도 매번 갱신 이벤트가
     * 뜨도록 보내는 시점의 타임스탬프를 함께 싣는다. 워치 쪽은 이 값을 읽지 않는다. */
    const val KEY_UPDATED_AT = "updatedAt"
    /** 워치에서 누른 버튼이 GPS 위치를 못 잡아 실제로는 기록되지 않았을 때, 그 실패
     * 시각(epoch ms)을 싣는다. 워치는 이 값이 이전에 본 값보다 커지면(=새로운 실패)
     * 진동으로 알려준다 — 폰 화면의 "위치를 가져오지 못해 기록되지 않았습니다" Toast와
     * 같은 의도지만, 워치는 화면이 작아 텍스트 대신 햅틱으로 대신한다. */
    const val KEY_LAST_FAILED_AT = "lastFailedAt"
    /** 이 라운드가 샷 GPS를 워치 기준으로 기록하는지(true) 폰 기준인지(false) —
     * 코스 선택 화면에서 라운드 시작 시 정한 값. 워치는 이 값이 true일 때만 자체
     * GPS를 계속 추적하고, INCREMENT_* 액션에 좌표를 실어 보낸다. */
    const val KEY_USE_WATCH_LOCATION = "useWatchLocation"
}
