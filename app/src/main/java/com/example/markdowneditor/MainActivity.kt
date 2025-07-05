package com.example.markdowneditor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.markdowneditor.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL


class MainActivity : AppCompatActivity() {

    private var _binding: ActivityMainBinding? = null
    private val binding
        get() = requireNotNull(_binding) {
            "Binding for ActivityMainBinding must not be null"
        }
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { loadFromFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnFile.setOnClickListener {
            openFilePicker()
        }

        binding.btnUrl.setOnClickListener {
            val url = binding.etUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                loadFromUrl(url)
            } else {
                Toast.makeText(this, "Введите URL", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openFilePicker() {
        filePickerLauncher.launch("text/*")
    }

    private fun loadFromFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
                            lines.joinToString("\n")
                        }
                    } ?: ""
                }
                if (content.isNotEmpty()) {
                    openMarkdownViewer(content)
                } else {
                    Toast.makeText(this@MainActivity, "Файл пуст", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "Ошибка загрузки файла: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun loadFromUrl(urlString: String) {
        lifecycleScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    val url = URL(urlString)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000

                    BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                        reader.readText()
                    }
                }

                if (content.isNotEmpty()) {
                    openMarkdownViewer(content)
                } else {
                    Toast.makeText(this@MainActivity, "Файл URL пуст", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "Ошибка загрузки URL: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun openMarkdownViewer(content: String) {
        val intent = Intent(this, MarkdownViewerActivity::class.java)
        intent.putExtra("markdown_content", content)
        startActivity(intent)
    }
}