package com.golfrecorder.wearsync

/**
 * 폰 앱(:app)과 워치 앱(:wear)이 Data Layer로 주고받는 경로/키 상수.
 * 두 모듈은 별도 Gradle 모듈이라 소스를 공유하지 않으므로, 이 파일은
 * `wear/src/main/kotlin/com/golfrecorder/wearsync/WearSync.kt`에 내용을
 * 그대로 복제해서 둔다 — 한쪽만 고치면 통신이 깨지니 항상 같이 수정할 것.
 */
object WearSync {
    /** 워치 → 폰: 버튼 입력 명령. MessageClient, payload는 아래 ACTION_* 문자열 UTF-8 바이트. */
    const val ACTION_PATH = "/stroke/action"

    /** 폰 → 워치: 최신 라운드 상태. DataClient DataMap. */
    const val STATE_PATH = "/round-state"

    const val ACTION_INCREMENT_TO_GREEN = "INCREMENT_TO_GREEN"
    const val ACTION_DECREMENT_TO_GREEN = "DECREMENT_TO_GREEN"
    const val ACTION_INCREMENT_SHORT_GAME = "INCREMENT_SHORT_GAME"
    const val ACTION_DECREMENT_SHORT_GAME = "DECREMENT_SHORT_GAME"
    const val ACTION_NEXT_HOLE = "NEXT_HOLE"
    const val ACTION_PREV_HOLE = "PREV_HOLE"

    const val KEY_ROUND_ACTIVE = "roundActive"
    const val KEY_HOLE_NUMBER = "holeNumber"
    const val KEY_PAR = "par"
    const val KEY_STROKES_TO_GREEN = "strokesToGreen"
    const val KEY_STROKES_SHORT_GAME = "strokesGreenToHoleOut"
    const val KEY_HOLE_COUNT = "holeCount"
    /** DataItem은 내용이 바뀌지 않으면 재전송해도 워치의 onDataChanged가 다시 호출되지
     * 않는다(내용 해시 기준 중복 제거) — 값이 우연히 이전과 같아도 매번 갱신 이벤트가
     * 뜨도록 보내는 시점의 타임스탬프를 함께 싣는다. 워치 쪽은 이 값을 읽지 않는다. */
    const val KEY_UPDATED_AT = "updatedAt"
}
