package com.example.markdowneditor

import android.content.ContentValues
import android.content.Intent
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Patterns
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.markdowneditor.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class MainActivity : AppCompatActivity() {

    private var _binding: ActivityMainBinding? = null
    private val binding get() = requireNotNull(_binding) { "Binding must not be null" }

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            contentResolver.takePersistableUriPermission(
                it,
                FLAG_GRANT_READ_URI_PERMISSION or FLAG_GRANT_WRITE_URI_PERMISSION
            )
            openViewer(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnFile.setOnClickListener {
            filePicker.launch(arrayOf("text/*"))
        }
        binding.btnUrl.setOnClickListener { handleUrlInput() }
    }

    private fun handleUrlInput() {
        val url = binding.etUrl.text.toString().trim()
        if (url.isEmpty()) {
            Toast.makeText(this, "Введите URL", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isValidUrl(url)) {
            Toast.makeText(this, "Введите корректный URL", Toast.LENGTH_LONG).show()
            return
        }

        showFilenameDialog(url)
    }

    private fun isValidUrl(url: String): Boolean {
        return try {
            Patterns.WEB_URL.matcher(url).matches()
        } catch (e: Exception) {
            false
        }
    }

    private fun showFilenameDialog(url: String) {
        val input = EditText(this).apply {
            hint = "document.md"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }

        AlertDialog.Builder(this)
            .setTitle("Имя файла")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val filename = input.text.toString().trim()
                if (filename.isNotEmpty()) {
                    downloadAndSave(url, filename)
                } else {
                    Toast.makeText(this, "Введите имя файла", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun downloadAndSave(url: String, filename: String) {
        lifecycleScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    URL(url).openStream().bufferedReader().use { it.readText() }
                }

                if (content.isBlank()) {
                    Toast.makeText(this@MainActivity, "Файл пуст", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val finalName = if (filename.endsWith(".md")) filename else "$filename.md"
                val uri = createFile(finalName, content)
                openViewer(uri)

            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "Ошибка загрузки: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun createFile(filename: String, content: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/markdown")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/Markdown")
        }

        return contentResolver.insert(
            MediaStore.Files.getContentUri("external"),
            values
        )?.also { uri ->
            contentResolver.openOutputStream(uri)?.use {
                it.write(content.toByteArray())
            }
        }
    }

    private fun openViewer(uri: Uri?) {
        startActivity(
            Intent(this, MarkdownViewerActivity::class.java).apply {
                putExtra("file_uri", uri.toString())
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}