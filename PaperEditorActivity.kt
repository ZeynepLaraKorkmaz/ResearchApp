package com.example.researchapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class PaperEditorActivity : AppCompatActivity() {

    private lateinit var paperTitle: EditText
    private lateinit var paperContent: EditText
    private lateinit var saveDraftBtn: Button
    private lateinit var exportPdfBtn: Button
    private lateinit var shareBtn: Button
    private lateinit var sharedStatusTextView: TextView

    private val client = OkHttpClient()
    private val baseUrl = "http://10.0.2.2:5000" // Emulator için localhost

    private var isEditMode = false
    private var paperId: Int? = null
    private var userId: Int = -1
    private var paperOwnerId: Int = -1
    private var isShared: Boolean = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.all { it.value }
        if (granted) {
            exportCurrentPaperToPdfInternal()
        } else {
            Toast.makeText(this, "İzinler verilmedi, PDF kaydedilemiyor", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_paper_editor)

        paperTitle = findViewById(R.id.paperTitle)
        paperContent = findViewById(R.id.paperContent)
        saveDraftBtn = findViewById(R.id.saveDraftBtn)
        exportPdfBtn = findViewById(R.id.exportPdfBtn)
        shareBtn = findViewById(R.id.shareBtn)
        sharedStatusTextView = findViewById(R.id.sharedStatusTextView)

        userId = intent.getIntExtra("user_id", -1)
        paperId = intent.getIntExtra("paper_id", -1).takeIf { it != -1 }
        paperOwnerId = intent.getIntExtra("paper_owner_id", -1)
        isEditMode = paperId != null

        if (isEditMode) {
            loadPaperFromIntent()
        }

        saveDraftBtn.setOnClickListener {
            if (isEditMode) updatePaper() else addPaper()
        }

        exportPdfBtn.setOnClickListener {
            if (hasStoragePermissions()) {
                exportCurrentPaperToPdfInternal()
            } else {
                requestStoragePermissions()
            }
        }

        shareBtn.setOnClickListener {
            if (paperOwnerId != userId) {
                Toast.makeText(this, "Bu makaleyi paylaşamazsınız", Toast.LENGTH_SHORT).show()
            } else {
                isShared = !isShared
                updateSharedStatusUI()
                if (isEditMode) updatePaper()
            }
        }
    }

    private fun loadPaperFromIntent() {
        paperTitle.setText(intent.getStringExtra("paper_title") ?: "")
        paperContent.setText(intent.getStringExtra("paper_content") ?: "")
        isShared = intent.getBooleanExtra("paper_shared", false)
        updateSharedStatusUI()
    }

    private fun updateSharedStatusUI() {
        sharedStatusTextView.text = if (isShared) "Paylaşıldı" else "Paylaşılmadı"
        sharedStatusTextView.setTextColor(
            if (isShared) 0xFF008000.toInt() else 0xFFFF0000.toInt()
        )
    }

    private fun addPaper() {
        val title = paperTitle.text.toString().trim()
        val content = paperContent.text.toString().trim()

        if (title.isBlank()) {
            Toast.makeText(this, "Başlık boş olamaz", Toast.LENGTH_SHORT).show()
            return
        }

        val json = JSONObject().apply {
            put("title", title)
            put("content", content)
            put("user_id", userId)
            put("shared", isShared)
        }

        val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("$baseUrl/add_paper")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@PaperEditorActivity, "Kayıt başarısız: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        val resJson = JSONObject(it.body!!.string())
                        paperId = resJson.optInt("paper_id")
                        paperOwnerId = userId
                        isEditMode = true
                        runOnUiThread {
                            Toast.makeText(this@PaperEditorActivity, "Kayıt başarılı", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this@PaperEditorActivity, "Kayıt başarısız: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        })
    }

    private fun updatePaper() {
        val title = paperTitle.text.toString().trim()
        val content = paperContent.text.toString().trim()

        if (paperId == null) {
            Toast.makeText(this, "Geçerli bir makale yok", Toast.LENGTH_SHORT).show()
            return
        }
        if (title.isBlank()) {
            Toast.makeText(this, "Başlık boş olamaz", Toast.LENGTH_SHORT).show()
            return
        }

        val json = JSONObject().apply {
            put("paper_id", paperId)
            put("title", title)
            put("content", content)
            put("user_id", userId)
            put("shared", isShared)
        }

        val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("$baseUrl/update_paper")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@PaperEditorActivity, "Güncelleme başarısız: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(this@PaperEditorActivity, "Güncelleme başarılı", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@PaperEditorActivity, "Güncelleme başarısız: ${response.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun exportCurrentPaperToPdfInternal() {
        val title = paperTitle.text.toString().trim()
        val content = paperContent.text.toString().trim()

        if (title.isBlank() || content.isBlank()) {
            Toast.makeText(this, "Başlık ve içerik boş olamaz", Toast.LENGTH_SHORT).show()
            return
        }

        val json = JSONObject().apply {
            put("title", title)
            put("content", content)
        }

        val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("$baseUrl/export_pdf")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@PaperEditorActivity, "PDF oluşturulamadı: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    runOnUiThread {
                        Toast.makeText(this@PaperEditorActivity, "PDF oluşturulamadı: ${response.message}", Toast.LENGTH_SHORT).show()
                    }
                    return
                }

                val bytes = response.body?.bytes()
                if (bytes == null) {
                    runOnUiThread {
                        Toast.makeText(this@PaperEditorActivity, "PDF verisi boş", Toast.LENGTH_SHORT).show()
                    }
                    return
                }

                val pdfFile = savePdfFile(bytes)
                if (pdfFile != null) {
                    runOnUiThread {
                        Toast.makeText(this@PaperEditorActivity, "PDF kaydedildi: ${pdfFile.absolutePath}", Toast.LENGTH_SHORT).show()
                        openPdfFile(pdfFile)
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@PaperEditorActivity, "PDF kaydedilemedi", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun hasStoragePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            true
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportCurrentPaperToPdfInternal()
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            )
        }
    }

    private fun savePdfFile(pdfData: ByteArray): File? {
        return try {
            val pdfDir = File(getExternalFilesDir(null), "pdfs")
            if (!pdfDir.exists()) pdfDir.mkdirs()
            val pdfFile = File(pdfDir, "paper_${System.currentTimeMillis()}.pdf")
            FileOutputStream(pdfFile).use { it.write(pdfData) }
            pdfFile
        } catch (e: Exception) {
            null
        }
    }

    private fun openPdfFile(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NO_HISTORY
        }

        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "PDF görüntüleyici bulunamadı", Toast.LENGTH_SHORT).show()
        }
    }
}
