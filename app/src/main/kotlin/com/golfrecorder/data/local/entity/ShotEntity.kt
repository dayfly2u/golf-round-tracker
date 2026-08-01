package com.golfrecorder.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shots",
    foreignKeys = [
        ForeignKey(
            entity = RoundEntity::class,
            parentColumns = ["id"],
            childColumns = ["roundId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["roundId", "holeNumber", "phase", "shotIndex"], unique = true)]
)
data class ShotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roundId: Long,
    val holeNumber: Int,
    val phase: String,
    val shotIndex: Int,
    val lat: Double,
    val lng: Double,
    val capturedAt: Long
)
