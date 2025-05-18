package com.example.researchapp

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class CitationManagerActivity : AppCompatActivity() {

    private lateinit var citationList: RecyclerView
    private lateinit var citationAdapter: CitationAdapter
    private lateinit var addCitationButton: Button
    private lateinit var inputText: EditText
    private val citations = mutableListOf<String>()

    private val client = OkHttpClient()
    private val BASE_URL = "http://10.0.2.2:5000"

    private var paperId: Int = -1  // Dinamik paper_id, default -1 (geçersiz)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_citation_manager)

        citationList = findViewById(R.id.citationRecyclerView)
        addCitationButton = findViewById(R.id.addCitationButton)
        inputText = findViewById(R.id.citationInputEditText)

        citationAdapter = CitationAdapter(citations)
        citationList.layoutManager = LinearLayoutManager(this)
        citationList.adapter = citationAdapter

        // Intent ile paper_id'yi alıyoruz
        paperId = intent.getIntExtra("paper_id", -1)
        if (paperId == -1) {
            Toast.makeText(this, "Geçersiz paper ID!", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        loadCitations()

        addCitationButton.setOnClickListener {
            val input = inputText.text.toString().trim()
            if (input.isNotEmpty()) {
                generateCitation(input)
            } else {
                Toast.makeText(this, "Lütfen metin giriniz", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadCitations() {
        val url = "$BASE_URL/citations/paper/$paperId"

        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@CitationManagerActivity, "Sunucuya bağlanılamadı", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { bodyStr ->
                    val jsonArray = JSONArray(bodyStr)
                    citations.clear()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val citationText = obj.getString("citation_text")
                        citations.add(citationText)
                    }
                    runOnUiThread {
                        citationAdapter.notifyDataSetChanged()
                    }
                }
            }
        })
    }

    private fun generateCitation(text: String) {
        if (paperId == null || text.isEmpty()) {
            runOnUiThread {
                Toast.makeText(
                    this@CitationManagerActivity,
                    "Eksik bilgi: paperId veya atıf metni boş",
                    Toast.LENGTH_LONG
                ).show()
            }
            return
        }

        val url = "$BASE_URL/citations/generate"
        val json = JSONObject()
        json.put("paper_id", paperId)
        json.put("text", text)

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toString().toRequestBody(mediaType)

        val request = Request.Builder().url(url).post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(
                        this@CitationManagerActivity,
                        "AI atıf oluşturulamadı",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { bodyStr ->
                    val jsonResponse = JSONObject(bodyStr)

                    if (jsonResponse.has("citation")) {
                        val newCitation = jsonResponse.getString("citation")
                        citations.add(newCitation)
                        runOnUiThread {
                            citationAdapter.notifyDataSetChanged()
                            inputText.text.clear()
                            Toast.makeText(
                                this@CitationManagerActivity,
                                "Atıf oluşturuldu",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else if (jsonResponse.has("error")) {
                        val error = jsonResponse.getString("error")
                        runOnUiThread {
                            Toast.makeText(
                                this@CitationManagerActivity,
                                "Hata: $error",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(
                                this@CitationManagerActivity,
                                "Bilinmeyen sunucu yanıtı",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        })
    }



}

class CitationAdapter(private val citations: List<String>) : RecyclerView.Adapter<CitationAdapter.CitationViewHolder>() {

    inner class CitationViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val citationTextView: TextView = itemView.findViewById(R.id.citationTextView)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): CitationViewHolder {
        val view = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_citation, parent, false)
        return CitationViewHolder(view)
    }

    override fun onBindViewHolder(holder: CitationViewHolder, position: Int) {
        holder.citationTextView.text = citations[position]
    }

    override fun getItemCount(): Int = citations.size
}
