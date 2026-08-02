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

    @Query(
        "SELECT * FROM shots WHERE roundId = :roundId AND holeNumber = :holeNumber " +
            "ORDER BY CASE phase WHEN 'TO_GREEN' THEN 0 ELSE 1 END, shotIndex"
    )
    fun getShots(roundId: Long, holeNumber: Int): Flow<List<ShotEntity>>
}
