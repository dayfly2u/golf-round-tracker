package com.golfrecorder.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "hole_records",
    foreignKeys = [
        ForeignKey(
            entity = RoundEntity::class,
            parentColumns = ["id"],
            childColumns = ["roundId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["roundId", "holeNumber"], unique = true)]
)
data class HoleRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roundId: Long,
    val holeNumber: Int,
    val par: Int,
    val strokesToGreen: Int,
    val strokesGreenToHoleOut: Int,
    /** strokesGreenToHoleOut 중 퍼팅(그린 위) 타수만 따로 — 숏어프로치는
     * strokesGreenToHoleOut - strokesPutt로 구한다(별도 컬럼 없이 파생). */
    val strokesPutt: Int = 0
)
