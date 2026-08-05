package com.golfrecorder.data.repository

import com.golfrecorder.data.local.dao.RoundDao
import com.golfrecorder.data.local.dto.RoundSummary
import com.golfrecorder.data.local.entity.HoleRecordEntity
import com.golfrecorder.data.local.entity.RoundEntity
import com.golfrecorder.data.local.relation.RoundWithHoleRecords
import kotlinx.coroutines.flow.Flow

class RoundRepository(private val roundDao: RoundDao) {

    suspend fun startRound(courseId: Long, courseName: String, playedAt: Long, memo: String? = null): Long =
        roundDao.insertRound(
            RoundEntity(courseId = courseId, courseName = courseName, playedAt = playedAt, memo = memo)
        )

    suspend fun finishRound(roundId: Long, finishedAt: Long) =
        roundDao.updateFinishedAt(roundId, finishedAt)

    suspend fun saveHoleRecord(
        roundId: Long,
        holeNumber: Int,
        par: Int,
        strokesToGreen: Int,
        strokesGreenToHoleOut: Int
    ) = roundDao.upsertHoleRecord(
        HoleRecordEntity(
            roundId = roundId,
            holeNumber = holeNumber,
            par = par,
            strokesToGreen = strokesToGreen,
            strokesGreenToHoleOut = strokesGreenToHoleOut
        )
    )

    fun getRoundWithHoleRecords(roundId: Long): Flow<RoundWithHoleRecords?> =
        roundDao.getRoundWithHoleRecords(roundId)

    fun getRoundSummaries(): Flow<List<RoundSummary>> = roundDao.getRoundSummaries()

    suspend fun deleteRound(roundId: Long) = roundDao.deleteRound(roundId)
}
