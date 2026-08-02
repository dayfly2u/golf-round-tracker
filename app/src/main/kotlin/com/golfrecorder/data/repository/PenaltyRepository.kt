package com.golfrecorder.data.repository

import com.golfrecorder.data.local.dao.PenaltyDao
import com.golfrecorder.data.local.entity.PenaltyEntity
import com.golfrecorder.domain.model.PenaltyType
import com.golfrecorder.domain.model.ShotPhase
import kotlinx.coroutines.flow.Flow

class PenaltyRepository(private val penaltyDao: PenaltyDao) {

    fun getPenalties(roundId: Long, holeNumber: Int): Flow<List<PenaltyEntity>> =
        penaltyDao.getPenalties(roundId, holeNumber)

    suspend fun addPenalty(
        roundId: Long,
        holeNumber: Int,
        phase: ShotPhase,
        type: PenaltyType,
        penaltyIndex: Int,
        lat: Double,
        lng: Double,
    ) = penaltyDao.upsertPenalty(
        PenaltyEntity(
            roundId = roundId,
            holeNumber = holeNumber,
            phase = phase.name,
            type = type.name,
            penaltyIndex = penaltyIndex,
            lat = lat,
            lng = lng,
            capturedAt = System.currentTimeMillis(),
        )
    )

    suspend fun removePenalty(roundId: Long, holeNumber: Int, phase: ShotPhase, type: PenaltyType, penaltyIndex: Int) =
        penaltyDao.deletePenalty(roundId, holeNumber, phase.name, type.name, penaltyIndex)
}
