package com.example.researchapp

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class ReferenceLibraryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ReferenceAdapter
    private val client = OkHttpClient()

    private var userId: Int = 1 // default user ID, will be overridden by intent

    private val baseUrl = "http://10.0.2.2:5000"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reference_library)

        // Intent'ten userId alıyoruz
        userId = intent.getIntExtra("user_id", 1)

        recyclerView = findViewById(R.id.rvReferences)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ReferenceAdapter(mutableListOf()) { ref ->
            // Uzun basınca silme onayı göster
            AlertDialog.Builder(this)
                .setTitle("Delete Reference")
                .setMessage("Are you sure you want to delete \"${ref.title}\"?")
                .setPositiveButton("Delete") { dialog, _ ->
                    deleteReference(ref.id)
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                .show()
        }
        recyclerView.adapter = adapter

        findViewById<Button>(R.id.btnAddReference).setOnClickListener {
            showAddReferenceDialog()
        }

        loadReferences()
    }

    private fun loadReferences() {
        val url = "$baseUrl/references/$userId"
        val request = Request.Builder().url(url).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@ReferenceLibraryActivity, "Failed to load references: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    runOnUiThread {
                        Toast.makeText(this@ReferenceLibraryActivity, "Failed to load references: ${response.message}", Toast.LENGTH_LONG).show()
                    }
                    return
                }

                val body = response.body?.string()
                if (body == null) {
                    runOnUiThread {
                        Toast.makeText(this@ReferenceLibraryActivity, "Response body is null", Toast.LENGTH_LONG).show()
                    }
                    return
                }

                try {
                    val jsonArray = JSONArray(body)
                    val references = mutableListOf<Reference>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        references.add(
                            Reference(
                                obj.getInt("id"),
                                obj.optInt("user_id", 0),
                                obj.optString("author", ""),
                                obj.optString("category", ""),
                                obj.optString("title", "")
                            )
                        )
                    }
                    runOnUiThread {
                        adapter.updateData(references)
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this@ReferenceLibraryActivity, "Invalid data format: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    private fun showAddReferenceDialog() {
        val authorInput = EditText(this).apply { hint = "Author" }
        val categoryInput = EditText(this).apply { hint = "Category" }
        val titleInput = EditText(this).apply { hint = "Title" }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(authorInput)
            addView(categoryInput)
            addView(titleInput)
            setPadding(50, 40, 50, 10)
        }

        AlertDialog.Builder(this)
            .setTitle("Add Reference")
            .setView(layout)
            .setPositiveButton("Add") { dialog, _ ->
                val author = authorInput.text.toString().trim()
                val category = categoryInput.text.toString().trim()
                val title = titleInput.text.toString().trim()

                if (author.isEmpty() || category.isEmpty() || title.isEmpty()) {
                    Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show()
                } else {
                    addReference(author, category, title)
                    dialog.dismiss()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun addReference(author: String, category: String, title: String) {
        val url = "$baseUrl/references"
        val json = JSONObject().apply {
            put("user_id", userId)
            put("author", author)
            put("category", category)
            put("title", title)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toString().toRequestBody(mediaType)
        val request = Request.Builder().url(url).post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@ReferenceLibraryActivity, "Failed to add reference: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    runOnUiThread {
                        Toast.makeText(this@ReferenceLibraryActivity, "Reference added successfully", Toast.LENGTH_SHORT).show()
                        loadReferences()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@ReferenceLibraryActivity, "Add failed: ${response.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    private fun deleteReference(refId: Int) {
        val url = "$baseUrl/references/$refId"
        val request = Request.Builder().url(url).delete().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@ReferenceLibraryActivity, "Failed to delete: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    runOnUiThread {
                        Toast.makeText(this@ReferenceLibraryActivity, "Reference deleted", Toast.LENGTH_SHORT).show()
                        loadReferences()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@ReferenceLibraryActivity, "Delete failed: ${response.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }
}

data class Reference(
    val id: Int,
    val user_id: Int,
    val author: String,
    val category: String,
    val title: String
)

class ReferenceAdapter(
    private var references: MutableList<Reference>,
    private val onDeleteClick: (Reference) -> Unit
) : RecyclerView.Adapter<ReferenceAdapter.ReferenceViewHolder>() {

    fun updateData(newReferences: List<Reference>) {
        references.clear()
        references.addAll(newReferences)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ReferenceViewHolder {
        val view = android.view.LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
        return ReferenceViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReferenceViewHolder, position: Int) {
        val ref = references[position]
        holder.bind(ref)

        holder.itemView.setOnLongClickListener {
            onDeleteClick(ref)
            true
        }
    }

    override fun getItemCount(): Int = references.size

    class ReferenceViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val text1: android.widget.TextView = itemView.findViewById(android.R.id.text1)
        private val text2: android.widget.TextView = itemView.findViewById(android.R.id.text2)

        fun bind(ref: Reference) {
            text1.text = ref.title
            text2.text = "Author: ${ref.author}, Category: ${ref.category}"
        }
    }
}
