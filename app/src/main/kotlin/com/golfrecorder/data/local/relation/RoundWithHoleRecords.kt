package com.golfrecorder.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.golfrecorder.data.local.entity.HoleRecordEntity
import com.golfrecorder.data.local.entity.RoundEntity

data class RoundWithHoleRecords(
    @Embedded val round: RoundEntity,
    @Relation(parentColumn = "id", entityColumn = "roundId")
    val holeRecords: List<HoleRecordEntity>
)
