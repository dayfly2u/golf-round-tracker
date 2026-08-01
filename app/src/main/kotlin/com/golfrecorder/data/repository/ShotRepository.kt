package com.golfrecorder.data.repository

import com.golfrecorder.data.local.dao.ShotDao
import com.golfrecorder.data.local.entity.ShotEntity
import com.golfrecorder.domain.model.ShotPhase
import kotlinx.coroutines.flow.Flow

class ShotRepository(private val shotDao: ShotDao) {

    fun getShots(roundId: Long, holeNumber: Int): Flow<List<ShotEntity>> =
        shotDao.getShots(roundId, holeNumber)

    suspend fun recordShot(
        roundId: Long,
        holeNumber: Int,
        phase: ShotPhase,
        shotIndex: Int,
        lat: Double,
        lng: Double,
    ) = shotDao.upsertShot(
        ShotEntity(
            roundId = roundId,
            holeNumber = holeNumber,
            phase = phase.name,
            shotIndex = shotIndex,
            lat = lat,
            lng = lng,
            capturedAt = System.currentTimeMillis(),
        )
    )

    suspend fun removeShot(roundId: Long, holeNumber: Int, phase: ShotPhase, shotIndex: Int) =
        shotDao.deleteShot(roundId, holeNumber, phase.name, shotIndex)
}
