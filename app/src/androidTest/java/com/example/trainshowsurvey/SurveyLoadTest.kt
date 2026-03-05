package com.example.trainshowsurvey

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.trainshowsurvey.data.Survey
import com.example.trainshowsurvey.data.SurveyDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SurveyLoadTest {

    @Test
    fun load2000Surveys() = runBlocking {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val database = SurveyDatabase.getDatabase(appContext)
        val surveyDao = database.surveyDao()

        val sources = listOf("Facebook", "Youtube", "TikTok", "Flyer", "Sign", "Friend/Family")

        repeat(10000) { i ->
            val survey = Survey(
                timestamp = System.currentTimeMillis() - (i * 1000), // Spaced out timestamps
                adults = (1..5).random(),
                children = (0..3).random(),
                sources = sources.shuffled().take((1..3).random()).joinToString(", ")
            )
            surveyDao.insert(survey)
        }
    }
}
