package com.golfrecorder.domain.model

/** 이 라운드에서 샷 GPS 좌표를 어느 기기 기준으로 기록할지. 카트를 타면 폰이 카트에
 * 남아있는 경우가 많아, 그럴 땐 워치 GPS를 쓰는 게 더 정확하다. 라운드 시작 시
 * 코스 선택 화면에서 한 번 고르면 그 라운드 내내 고정된다. */
enum class LocationSource {
    PHONE,
    WATCH,
}
