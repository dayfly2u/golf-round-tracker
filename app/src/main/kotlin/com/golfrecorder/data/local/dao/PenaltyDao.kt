package com.golfrecorder.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.golfrecorder.data.local.entity.PenaltyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PenaltyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPenalty(penalty: PenaltyEntity)

    @Query(
        "DELETE FROM penalties WHERE roundId = :roundId AND holeNumber = :holeNumber " +
            "AND phase = :phase AND type = :type AND penaltyIndex = :penaltyIndex"
    )
    suspend fun deletePenalty(roundId: Long, holeNumber: Int, phase: String, type: String, penaltyIndex: Int)

    @Query("SELECT * FROM penalties WHERE roundId = :roundId AND holeNumber = :holeNumber")
    fun getPenalties(roundId: Long, holeNumber: Int): Flow<List<PenaltyEntity>>
}
