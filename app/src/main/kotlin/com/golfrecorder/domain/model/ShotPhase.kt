package com.golfrecorder.domain.model

enum class ShotPhase {
    TO_GREEN,
    /** 숏어프로치(칩/피치) — 그린 밖에서 친 샷. */
    SHORT_GAME,
    /** 퍼팅 — 그린 위에서 친 샷. */
    PUTT,
}
