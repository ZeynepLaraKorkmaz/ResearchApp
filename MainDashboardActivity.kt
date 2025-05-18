package com.example.researchapp

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class MainDashboardActivity : AppCompatActivity() {

    private val BASE_URL = "http://10.0.2.2:5000"
    private var userId: Int = -1
    private lateinit var listView: ListView
    private lateinit var deleteButton: Button

    private val paperTitles = mutableListOf<String>()
    private val paperContents = mutableListOf<String>()
    private val paperIds = mutableListOf<Int>()
    private val paperSharedStatus = mutableListOf<Boolean>()
    private val paperOwnerIds = mutableListOf<Int>()

    private var selectedPosition: Int = -1

    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_dashboard)

        userId = intent.getIntExtra("user_id", -1)
        listView = findViewById(R.id.paperListView)
        deleteButton = findViewById(R.id.deletePaperButton)

        findViewById<Button>(R.id.addPaperButton).setOnClickListener {
            val intent = Intent(this, PaperEditorActivity::class.java).apply {
                putExtra("user_id", userId)
                // Yeni makale için paper_id gibi alanlar gönderilmez
            }
            startActivity(intent)
        }

        findViewById<Button>(R.id.gotoCitation).setOnClickListener {
            val intent = Intent(this, CitationManagerActivity::class.java).apply {
                putExtra("paper_id", 1) // Buraya dinamik olarak seçilen paper_id'yi koymalısın
            }
            startActivity(intent)
        }


        findViewById<Button>(R.id.gotoReference).setOnClickListener {
            if (userId > 0) {
                val intent = Intent(this, ReferenceLibraryActivity::class.java).apply {
                    putExtra("user_id", userId)
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Invalid user ID", Toast.LENGTH_SHORT).show()
            }
        }


        findViewById<Button>(R.id.gotoSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        deleteButton.isEnabled = false

        listView.setOnItemClickListener { _, _, position, _ ->
            selectedPosition = -1
            deleteButton.isEnabled = false
            updateListView()

            val intent = Intent(this, PaperEditorActivity::class.java).apply {
                putExtra("user_id", userId)
                putExtra("paper_id", paperIds[position])
                putExtra("paper_title", paperTitles[position])
                putExtra("paper_content", paperContents[position])
                putExtra("paper_owner_id", paperOwnerIds[position])
                putExtra("paper_shared", paperSharedStatus[position])
            }
            startActivity(intent)
        }

        listView.setOnItemLongClickListener { _, _, position, _ ->
            selectedPosition = position
            deleteButton.isEnabled = true
            updateListView()
            true
        }

        deleteButton.setOnClickListener {
            if (selectedPosition != -1 && selectedPosition < paperIds.size) {
                confirmDeleteDialog(selectedPosition)
            }
        }

        loadPapers()
    }

    override fun onResume() {
        super.onResume()
        loadPapers()
        selectedPosition = -1
        deleteButton.isEnabled = false
    }

    private fun updateListView() {
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, paperTitles) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent) as TextView
                val shared = paperSharedStatus.getOrNull(position) ?: false
                view.text = paperTitles[position] + if (shared) " (Shared)" else ""
                view.setBackgroundColor(
                    if (position == selectedPosition) Color.LTGRAY else Color.TRANSPARENT
                )
                return view
            }
        }
        listView.adapter = adapter
    }

    private fun loadPapers() {
        val jsonObj = JSONObject().apply {
            put("user_id", userId)
        }
        val requestBody = jsonObj.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder().url("$BASE_URL/load_papers").post(requestBody).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("MainDashboard", "Failed to load papers", e)
                runOnUiThread {
                    Toast.makeText(this@MainDashboardActivity, "Failed to load papers", Toast.LENGTH_SHORT).show()
                    clearPaperData()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    runOnUiThread {
                        Toast.makeText(this@MainDashboardActivity, "Server error: ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                    return
                }

                val bodyString = response.body?.string()
                if (bodyString.isNullOrEmpty()) {
                    runOnUiThread {
                        clearPaperData()
                        Toast.makeText(this@MainDashboardActivity, "No papers found", Toast.LENGTH_SHORT).show()
                    }
                    return
                }

                try {
                    val responseArray = JSONArray(bodyString)

                    paperTitles.clear()
                    paperContents.clear()
                    paperIds.clear()
                    paperSharedStatus.clear()
                    paperOwnerIds.clear()

                    for (i in 0 until responseArray.length()) {
                        val obj = responseArray.getJSONObject(i)
                        paperTitles.add(obj.getString("title"))
                        paperContents.add(obj.getString("content"))
                        paperIds.add(obj.getInt("id"))
                        paperOwnerIds.add(obj.getInt("owner_id"))
                        paperSharedStatus.add(obj.optBoolean("shared", false))
                    }

                    runOnUiThread {
                        if (paperTitles.isEmpty()) {
                            Toast.makeText(this@MainDashboardActivity, "No papers to display", Toast.LENGTH_SHORT).show()
                        }
                        selectedPosition = -1
                        deleteButton.isEnabled = false
                        updateListView()
                    }
                } catch (e: Exception) {
                    Log.e("MainDashboard", "Parsing error", e)
                    runOnUiThread {
                        Toast.makeText(this@MainDashboardActivity, "Parsing error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun clearPaperData() {
        paperTitles.clear()
        paperContents.clear()
        paperIds.clear()
        paperSharedStatus.clear()
        paperOwnerIds.clear()
        updateListView()
    }

    private fun confirmDeleteDialog(position: Int) {
        AlertDialog.Builder(this)
            .setTitle("Confirm Delete")
            .setMessage("Are you sure you want to delete \"${paperTitles[position]}\"?")
            .setPositiveButton("Delete") { _, _ ->
                deletePaper(paperIds[position])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deletePaper(paperId: Int) {
        val jsonObj = JSONObject().apply {
            put("paper_id", paperId)
            put("user_id", userId)
        }
        val requestBody = jsonObj.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder().url("$BASE_URL/delete_paper").post(requestBody).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("MainDashboard", "Failed to delete paper", e)
                runOnUiThread {
                    Toast.makeText(this@MainDashboardActivity, "Failed to delete paper", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    runOnUiThread {
                        Toast.makeText(this@MainDashboardActivity, "Server error on delete: ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                    return
                }

                runOnUiThread {
                    Toast.makeText(this@MainDashboardActivity, "Paper deleted", Toast.LENGTH_SHORT).show()
                    loadPapers()
                    selectedPosition = -1
                    deleteButton.isEnabled = false
                }
            }
        })
    }
}
