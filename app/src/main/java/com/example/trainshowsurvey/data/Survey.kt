package com.example.trainshowsurvey.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a single survey submission.
 */
@Entity(tableName = "surveys")
data class Survey(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timestamp: Long,
    val adults: Int,
    val children: Int,
    val sources: String
)