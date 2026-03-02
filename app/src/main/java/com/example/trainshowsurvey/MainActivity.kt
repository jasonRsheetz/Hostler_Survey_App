package com.example.trainshowsurvey

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import com.example.trainshowsurvey.data.Survey
import com.example.trainshowsurvey.data.SurveyDatabase
import com.google.android.material.card.MaterialCardView
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.AppendValuesResponse
import com.google.api.services.sheets.v4.model.ValueRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var database: SurveyDatabase
    private lateinit var googleSignInClient: GoogleSignInClient
    private var submitClickCounter = 0

    private val spreadsheetId = "1LFA6P29Rpaxiv_8g5in4KNapD1yr6uNK5WvBJJOOi-4"

    private val checkboxes by lazy {
        listOf<CheckBox>(
            findViewById(R.id.checkbox_facebook),
            findViewById(R.id.checkbox_youtube),
            findViewById(R.id.checkbox_tiktok),
            findViewById(R.id.checkbox_flyer),
            findViewById(R.id.checkbox_sign),
            findViewById(R.id.checkbox_friend)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        database = SurveyDatabase.getDatabase(this)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(SheetsScopes.SPREADSHEETS))
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val submitButton = findViewById<Button>(R.id.submit_button)
        val uploadButton = findViewById<Button>(R.id.upload_button)
        val adultCard = findViewById<MaterialCardView>(R.id.adult_card)
        val childCard = findViewById<MaterialCardView>(R.id.child_card)
        val surveyCard = findViewById<MaterialCardView>(R.id.survey_card)
        val thankYouText = findViewById<TextView>(R.id.thank_you_text)
        val adultCountText = findViewById<TextView>(R.id.adult_count_text)
        val adultincrementButton = findViewById<Button>(R.id.adult_increment_button)
        val adultdecrementButton = findViewById<Button>(R.id.adult_decrement_button)
        val childCountText = findViewById<TextView>(R.id.child_count_text)
        val childincrementButton = findViewById<Button>(R.id.child_increment_button)
        val childdecrementButton = findViewById<Button>(R.id.child_decrement_button)

        adultincrementButton.setOnClickListener {
            val currentCount = adultCountText.text.toString().toInt()
            adultCountText.text = (currentCount + 1).toString()
        }

        adultdecrementButton.setOnClickListener {
            val currentCount = adultCountText.text.toString().toInt()
            if (currentCount > 1) {
                adultCountText.text = (currentCount - 1).toString()
            }
        }

        childincrementButton.setOnClickListener {
            val childcurrentCount = childCountText.text.toString().toInt()
            childCountText.text = (childcurrentCount + 1).toString()
        }

        childdecrementButton.setOnClickListener {
            val childcurrentCount = childCountText.text.toString().toInt()
            if (childcurrentCount > 0) {
                childCountText.text = (childcurrentCount - 1).toString()
            }
        }

        submitButton.setOnClickListener {
            try {
                val selectedSources = checkboxes.filter { it.isChecked }.map { it.text.toString() }

                if (selectedSources.isNotEmpty()) {
                    val sourceString = selectedSources.joinToString(", ")
                    val timestamp = System.currentTimeMillis()
                    val adultCountValue = adultCountText.text.toString().toInt()
                    val childCountValue = childCountText.text.toString().toInt()

                    val survey = Survey(timestamp = timestamp, adults = adultCountValue, children = childCountValue, sources = sourceString)

                    lifecycleScope.launch {
                        try {
                            database.surveyDao().insert(survey)
                            checkboxes.forEach { it.isChecked = false }
                            adultCountText.text = "1"
                            childCountText.text = "0"

                            // Show thank you screen
                            adultCard.visibility = View.INVISIBLE
                            childCard.visibility = View.INVISIBLE
                            surveyCard.visibility = View.INVISIBLE
                            submitButton.visibility = View.INVISIBLE
                            uploadButton.visibility = View.INVISIBLE
                            thankYouText.visibility = View.VISIBLE

                            Handler(Looper.getMainLooper()).postDelayed({
                                adultCard.visibility = View.VISIBLE
                                childCard.visibility = View.VISIBLE
                                surveyCard.visibility = View.VISIBLE
                                submitButton.visibility = View.VISIBLE
                                thankYouText.visibility = View.GONE
                            }, 2000)
                        } catch (e: Exception) {
                            Log.e("SurveyCrash", "Database insert failed", e)
                            Toast.makeText(this@MainActivity, "DB Error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                    submitClickCounter = 0
                } else {
                    submitClickCounter++
                    if (submitClickCounter >= 7) {
                        uploadButton.visibility = View.VISIBLE
                        Toast.makeText(this@MainActivity, "Upload unlocked", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("SurveyCrash", "Submission processing failed", e)
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        uploadButton.setOnClickListener {
            signIn()
            it.visibility = View.GONE
            submitClickCounter = 0
        }
    }

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            lifecycleScope.launch {
                try {
                    Log.d("SheetsUpload", "Sign-in successful, attempting to get account and upload.")
                    val account = task.result
                    val credential = GoogleAccountCredential.usingOAuth2(
                        this@MainActivity,
                        listOf(SheetsScopes.SPREADSHEETS)
                    )
                    credential.selectedAccount = account.account

                    val sheetsService = Sheets.Builder(
                        NetHttpTransport(),
                        GsonFactory.getDefaultInstance(),
                        credential
                    ).setApplicationName("Hostlers Survey").build()

                    uploadData(sheetsService)
                } catch (e: Exception) {
                    Log.e("SheetsUpload", "Error during sign-in or creating Sheets service", e)
                    Toast.makeText(this@MainActivity, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun signIn() {
        Log.d("SheetsUpload", "Starting Google Sign-In flow.")
        val signInIntent: Intent = googleSignInClient.signInIntent
        signInLauncher.launch(signInIntent)
    }

    private suspend fun uploadData(sheetsService: Sheets) {
        withContext(Dispatchers.IO) {
            try {
                val surveys = database.surveyDao().getAllSurveys()
                if (surveys.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "No new surveys to upload.", Toast.LENGTH_SHORT).show()
                    }
                    return@withContext
                }

                val allOptions = listOf("Facebook", "Youtube", "TikTok", "Flyer", "Sign", "Friend/Family")
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

                val values = surveys.map { survey ->
                    val date = Date(survey.timestamp)
                    val formattedDate = sdf.format(date)
                    val selectedSources = survey.sources.split(", ").toSet()

                    val rowData = mutableListOf<Any>(formattedDate, survey.adults, survey.children)
                    allOptions.forEach { option ->
                        rowData.add(if (selectedSources.contains(option)) 1 else 0)
                    }
                    rowData
                }

                val body = ValueRange().setValues(values)
                val range = "Sheet1!A2"

                val result: AppendValuesResponse = sheetsService.spreadsheets().values()
                    .append(spreadsheetId, range, body)
                    .setValueInputOption("USER_ENTERED")
                    .execute()

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "${surveys.size} survey submissions uploaded.", Toast.LENGTH_SHORT).show()
                    // Clear the local database after successful upload
                    lifecycleScope.launch {
                        database.surveyDao().deleteAll()
                    }
                }
            } catch (e: Exception) {
                Log.e("SheetsUpload", "Error during data upload to Google Sheets", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}