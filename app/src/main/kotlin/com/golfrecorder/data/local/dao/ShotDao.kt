package com.golfrecorder.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.golfrecorder.data.local.entity.ShotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertShot(shot: ShotEntity)

    @Query(
        "DELETE FROM shots WHERE roundId = :roundId AND holeNumber = :holeNumber " +
            "AND phase = :phase AND shotIndex = :shotIndex"
    )
    suspend fun deleteShot(roundId: Long, holeNumber: Int, phase: String, shotIndex: Int)

    // ShotPhase.order()(TO_GREEN=0, SHORT_GAME=1, PUTT=2)와 반드시 같은 순서를 유지해야
    // 지도에서 샷을 잇는 선/거리 라벨이 올바르게 그려진다 — 숏어프로치/퍼팅이 분리되기
    // 전(2단계 시절)에는 TO_GREEN 여부만으로 충분했지만, 지금은 둘 다 "ELSE"로
    // 묶여서 shotIndex만으로 정렬되면 숏어프로치와 퍼팅이 뒤섞일 수 있다(예: 숏어프로치
    // shotIndex=1과 퍼팅 shotIndex=1이 동률이면 어느 쪽이 먼저 올지 보장되지 않는다).
    @Query(
        "SELECT * FROM shots WHERE roundId = :roundId AND holeNumber = :holeNumber " +
            "ORDER BY CASE phase " +
            "WHEN 'TO_GREEN' THEN 0 WHEN 'SHORT_GAME' THEN 1 WHEN 'PUTT' THEN 2 ELSE 3 END, " +
            "shotIndex"
    )
    fun getShots(roundId: Long, holeNumber: Int): Flow<List<ShotEntity>>
}
