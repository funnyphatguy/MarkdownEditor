package com.example.markdowneditor

import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.markdowneditor.databinding.ActivityMarkdownViewerBinding
import java.net.URL

class MarkdownViewerActivity : AppCompatActivity() {

    private var _binding: ActivityMarkdownViewerBinding? = null
    private val binding get() = requireNotNull(_binding) { "Binding must not be null" }

    private lateinit var fileUri: Uri
    private var markdownContent = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMarkdownViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uriString = intent.getStringExtra("file_uri") ?: run {
            finishWithError("Файл не найден")
            return
        }

        fileUri = Uri.parse(uriString)
        loadContent()
        setupButtons()
    }

    private fun loadContent() {
        try {
            contentResolver.openInputStream(fileUri)?.use { stream ->
                markdownContent = stream.bufferedReader().readText()
                renderMarkdown()
            } ?: run {
                finishWithError("Ошибка чтения файла")
            }
        } catch (e: Exception) {
            finishWithError("Ошибка: ${e.message}")
        }
    }

    private fun setupButtons() {
        binding.btnEdit.setOnClickListener {
            binding.markdownEditor.setText(markdownContent)
            binding.markdownEditor.visibility = View.VISIBLE
            binding.markdownPreviewContainer.visibility = View.GONE
            binding.btnSave.visibility = View.VISIBLE
            binding.btnEdit.visibility = View.GONE
        }

        binding.btnSave.setOnClickListener { saveChanges() }
    }

    private fun saveChanges() {
        markdownContent = binding.markdownEditor.text.toString()

        try {
            contentResolver.openOutputStream(fileUri)?.use {
                it.write(markdownContent.toByteArray())
                Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка сохранения: ${e.message}", Toast.LENGTH_LONG).show()
        }

        binding.markdownEditor.visibility = View.GONE
        binding.markdownPreviewContainer.visibility = View.VISIBLE
        binding.btnSave.visibility = View.GONE
        binding.btnEdit.visibility = View.VISIBLE

        binding.markdownContentContainer.removeAllViews()
        renderMarkdown()
    }

    private fun renderMarkdown() {
        markdownContent.lines().forEachIndexed { i, line ->
            when {
                line.startsWith("#") -> renderHeader(line)
                line.startsWith("![") -> renderImage(line)
                line.contains("|") && isTableStart(i) -> renderTable(i)
                line.isBlank() -> addEmptySpace()
                else -> renderText(line)
            }
        }
    }

    private fun renderHeader(line: String) {
        val level = line.takeWhile { it == '#' }.length
        val text = line.substring(level).trim()

        TextView(this).apply {
            setText(text)
            setTypeface(null, Typeface.BOLD)
            textSize = when (level) {
                1 -> 24f; 2 -> 22f; 3 -> 20f; 4 -> 18f; 5 -> 16f; else -> 14f
            }
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 16, 0, 8)
        }.also { binding.markdownContentContainer.addView(it) }
    }

    private fun renderText(line: String) {
        val spannable = SpannableString(line)

        "\\*\\*(.*?)\\*\\*".toRegex().findAll(line).forEach {
            spannable.setSpan(
                StyleSpan(Typeface.BOLD),
                it.range.start,
                it.range.endInclusive + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        "\\*(.*?)\\*".toRegex().findAll(line).forEach {
            spannable.setSpan(
                StyleSpan(Typeface.ITALIC),
                it.range.start,
                it.range.endInclusive + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        "~~(.*?)~~".toRegex().findAll(line).forEach {
            spannable.setSpan(
                StrikethroughSpan(),
                it.range.start,
                it.range.endInclusive + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        TextView(this).apply {
            setText(spannable)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 8, 0, 8)
        }.also { binding.markdownContentContainer.addView(it) }
    }

    private fun renderImage(line: String) {
        val url = "!\\[.*?\\]\\((.*?)\\)".toRegex().find(line)?.groupValues?.get(1) ?: return

        ImageView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                300
            ).apply {
                height = 300
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setPadding(0, 16, 0, 16)
            adjustViewBounds = true

            Thread {
                try {
                    val bitmap = BitmapFactory.decodeStream(URL(url).openStream())
                    runOnUiThread { setImageBitmap(bitmap) }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(
                            this@MarkdownViewerActivity,
                            "Ошибка загрузки изображения",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }.start()
        }.also { binding.markdownContentContainer.addView(it) }
    }

    private fun isTableStart(index: Int): Boolean {
        val nextLine = markdownContent.lines().getOrNull(index + 1) ?: return false
        return nextLine.contains("---")
    }

    private fun renderTable(startIndex: Int) {
        val lines = markdownContent.lines()
        TableLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            TableRow(this@MarkdownViewerActivity).apply {
                lines[startIndex].split("|").filter { it.isNotBlank() }.forEach { cell ->
                    addView(TextView(this@MarkdownViewerActivity).apply {
                        text = cell.trim()
                        setTypeface(null, Typeface.BOLD)
                        setPadding(8, 4, 8, 4)
                    })
                }
                addView(this)
            }

            var i = startIndex + 2
            while (i < lines.size && lines[i].contains("|")) {
                TableRow(this@MarkdownViewerActivity).apply {
                    lines[i].split("|").filter { it.isNotBlank() }.forEach { cell ->
                        addView(TextView(this@MarkdownViewerActivity).apply {
                            text = cell.trim()
                            setPadding(8, 4, 8, 4)
                        })
                    }
                    addView(this)
                }
                i++
            }
        }.also { binding.markdownContentContainer.addView(it) }
    }

    private fun addEmptySpace() {
        View(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                16
            )
        }.also { binding.markdownContentContainer.addView(it) }
    }

    private fun finishWithError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}