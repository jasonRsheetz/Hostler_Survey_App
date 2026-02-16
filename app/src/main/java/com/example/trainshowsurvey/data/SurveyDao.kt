package com.example.trainshowsurvey.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SurveyDao {
    @Insert
    suspend fun insert(survey: Survey)

    @Query("SELECT * FROM surveys")
    suspend fun getAllSurveys(): List<Survey>

    @Query("DELETE FROM surveys")
    suspend fun deleteAll()

    @Query("SELECT * FROM surveys ORDER BY id DESC LIMIT 1")
    suspend fun getLastSurvey(): Survey?
}