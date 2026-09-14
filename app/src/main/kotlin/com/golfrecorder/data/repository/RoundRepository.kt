package com.golfrecorder.data.repository

import com.golfrecorder.data.local.dao.RoundDao
import com.golfrecorder.data.local.dto.RoundSummary
import com.golfrecorder.data.local.entity.HoleRecordEntity
import com.golfrecorder.data.local.entity.RoundEntity
import com.golfrecorder.data.local.relation.RoundWithHoleRecords
import kotlinx.coroutines.flow.Flow

class RoundRepository(private val roundDao: RoundDao) {

    suspend fun startRound(
        courseId: Long,
        courseName: String,
        playedAt: Long,
        locationSource: String,
        mapProvider: String,
    ): Long =
        roundDao.insertRound(
            RoundEntity(
                courseId = courseId,
                courseName = courseName,
                playedAt = playedAt,
                locationSource = locationSource,
                mapProvider = mapProvider,
            )
        )

    suspend fun finishRound(roundId: Long, finishedAt: Long) =
        roundDao.updateFinishedAt(roundId, finishedAt)

    suspend fun updateRoundReview(roundId: Long, price: Int?, companions: String?, review: String?) =
        roundDao.updateRoundReview(roundId, price, companions, review)

    suspend fun saveHoleRecord(
        roundId: Long,
        holeNumber: Int,
        par: Int,
        strokesToGreen: Int,
        strokesGreenToHoleOut: Int,
        strokesPutt: Int
    ) = roundDao.upsertHoleRecord(
        HoleRecordEntity(
            roundId = roundId,
            holeNumber = holeNumber,
            par = par,
            strokesToGreen = strokesToGreen,
            strokesGreenToHoleOut = strokesGreenToHoleOut,
            strokesPutt = strokesPutt
        )
    )

    fun getRoundWithHoleRecords(roundId: Long): Flow<RoundWithHoleRecords?> =
        roundDao.getRoundWithHoleRecords(roundId)

    fun getRoundSummaries(): Flow<List<RoundSummary>> = roundDao.getRoundSummaries()

    suspend fun deleteRound(roundId: Long) = roundDao.deleteRound(roundId)

    suspend fun countRoundsForCourse(courseId: Long): Int = roundDao.countRoundsForCourse(courseId)

    suspend fun getCurrentHoleNumber(roundId: Long): Int? = roundDao.getCurrentHoleNumber(roundId)

    suspend fun updateCurrentHoleNumber(roundId: Long, holeNumber: Int) =
        roundDao.updateCurrentHoleNumber(roundId, holeNumber)

    suspend fun getLocationSource(roundId: Long): String? = roundDao.getLocationSource(roundId)

    suspend fun getMapProvider(roundId: Long): String? = roundDao.getMapProvider(roundId)
}
