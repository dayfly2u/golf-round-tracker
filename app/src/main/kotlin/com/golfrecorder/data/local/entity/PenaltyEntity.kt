package com.golfrecorder.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "penalties",
    foreignKeys = [
        ForeignKey(
            entity = RoundEntity::class,
            parentColumns = ["id"],
            childColumns = ["roundId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["roundId", "holeNumber", "phase", "type", "penaltyIndex"], unique = true)]
)
data class PenaltyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roundId: Long,
    val holeNumber: Int,
    val phase: String,
    val type: String,
    val penaltyIndex: Int,
    /** 이 벌타 하나가 실제로 더한 타수. OB 특설티처럼 한 번의 OB로 +2를 먹는 경우가 있다. */
    val strokeCount: Int,
    val lat: Double,
    val lng: Double,
    val capturedAt: Long
)
