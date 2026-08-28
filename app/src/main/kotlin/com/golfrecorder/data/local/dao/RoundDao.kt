package com.golfrecorder.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.golfrecorder.data.local.dto.RoundSummary
import com.golfrecorder.data.local.entity.HoleRecordEntity
import com.golfrecorder.data.local.entity.RoundEntity
import com.golfrecorder.data.local.relation.RoundWithHoleRecords
import kotlinx.coroutines.flow.Flow

@Dao
interface RoundDao {

    @Insert
    suspend fun insertRound(round: RoundEntity): Long

    @Query("UPDATE rounds SET finishedAt = :finishedAt WHERE id = :roundId")
    suspend fun updateFinishedAt(roundId: Long, finishedAt: Long)

    @Query(
        """
        UPDATE rounds SET
            price = :price,
            companions = :companions,
            review = :review
        WHERE id = :roundId
        """
    )
    suspend fun updateRoundReview(roundId: Long, price: Int?, companions: String?, review: String?)

    // roundId+holeNumber에 unique index가 걸려 있어서, 같은 홀을 다시 저장하면
    // REPLACE로 덮어씀 (라운드 중 스코어 수정 흐름을 그대로 지원)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHoleRecord(holeRecord: HoleRecordEntity)

    @Transaction
    @Query("SELECT * FROM rounds WHERE id = :roundId")
    fun getRoundWithHoleRecords(roundId: Long): Flow<RoundWithHoleRecords?>

    @Query(
        """
        SELECT r.id AS roundId, r.courseId AS courseId, r.playedAt AS playedAt,
               r.finishedAt AS finishedAt,
               COALESCE(c.name, r.courseName) AS courseName,
               r.price AS price, r.companions AS companions,
               SUM(hr.strokesToGreen + hr.strokesGreenToHoleOut) AS totalStrokes
        FROM rounds r
        LEFT JOIN courses c ON c.id = r.courseId
        LEFT JOIN hole_records hr ON hr.roundId = r.id
        GROUP BY r.id
        ORDER BY r.playedAt DESC
        """
    )
    fun getRoundSummaries(): Flow<List<RoundSummary>>

    @Query("DELETE FROM rounds WHERE id = :roundId")
    suspend fun deleteRound(roundId: Long)

    @Query("SELECT COUNT(*) FROM rounds WHERE courseId = :courseId")
    suspend fun countRoundsForCourse(courseId: Long): Int

    @Query("SELECT currentHoleNumber FROM rounds WHERE id = :roundId")
    suspend fun getCurrentHoleNumber(roundId: Long): Int?

    @Query("UPDATE rounds SET currentHoleNumber = :holeNumber WHERE id = :roundId")
    suspend fun updateCurrentHoleNumber(roundId: Long, holeNumber: Int)
}
