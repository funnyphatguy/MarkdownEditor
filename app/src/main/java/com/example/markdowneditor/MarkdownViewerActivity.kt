package com.example.markdowneditor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
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
    private val binding
        get() = requireNotNull(_binding) {
            "Binding for ActivityMarkdownViewerBinding must not be null"
        }
    private lateinit var markdownContent: String
    private val imageCache = mutableMapOf<String, Bitmap>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMarkdownViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        markdownContent = intent.getStringExtra("markdown_content") ?: ""
        if (markdownContent.isEmpty()) {
            Toast.makeText(this, "Документ пуст", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupUI()
        renderMarkdown(markdownContent)
    }

    private fun setupUI() {
        binding.btnEdit.setOnClickListener {
            enterEditMode()
        }

        binding.btnSave.setOnClickListener {
            saveChanges()
        }
    }

    private fun enterEditMode() {
        binding.markdownEditor.setText(markdownContent)
        binding.markdownEditor.visibility = View.VISIBLE
        binding.markdownPreviewContainer.visibility = View.GONE
        binding.btnSave.visibility = View.VISIBLE
        binding.btnEdit.visibility = View.GONE
    }

    private fun saveChanges() {
        markdownContent = binding.markdownEditor.text.toString()
        binding.markdownEditor.visibility = View.GONE
        binding.markdownPreviewContainer.visibility = View.VISIBLE
        binding.btnSave.visibility = View.GONE
        binding.btnEdit.visibility = View.VISIBLE

        binding.markdownContentContainer.removeAllViews()
        renderMarkdown(markdownContent)
    }

    private fun renderMarkdown(content: String) {
        val lines = content.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()

            when {
                line.startsWith("#") -> handleHeader(line)

                line.contains("|") && i + 1 < lines.size &&
                        lines[i + 1].contains("---") -> {
                    i = handleTable(lines, i)
                }

                line.startsWith("![") && line.endsWith(")") -> {
                    handleImage(line)
                }

                line.isEmpty() -> {
                    addEmptySpace()
                }

                else -> handleFormattedText(line)
            }
            i++
        }
    }

    private fun handleHeader(line: String) {
        val headerLevel = line.takeWhile { it == '#' }.length
        if (headerLevel in 1..6) {
            val headerText = line.substring(headerLevel).trim()
            val textView = TextView(this).apply {
                text = headerText
                textSize = when (headerLevel) {
                    1 -> 24f
                    2 -> 22f
                    3 -> 20f
                    4 -> 18f
                    5 -> 16f
                    6 -> 14f
                    else -> 14f
                }
                setTypeface(null, Typeface.BOLD)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(0, 16, 0, 8)
            }
            binding.markdownContentContainer.addView(textView)
        }
    }

    private fun handleFormattedText(line: String) {
        val textView = TextView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 8, 0, 8)
        }

        val spannable = SpannableString(line)

        applySpans(spannable, "(\\*\\*(.*?)\\*\\*)|(__(.*?)__)".toRegex(), StyleSpan(Typeface.BOLD))

        applySpans(spannable, "(\\*(.*?)\\*)|(_(.*?)_)".toRegex(), StyleSpan(Typeface.ITALIC))

        applySpans(spannable, "~~(.*?)~~".toRegex(), StrikethroughSpan())

        textView.text = spannable
        binding.markdownContentContainer.addView(textView)
    }

    private fun applySpans(
        spannable: SpannableString,
        pattern: Regex,
        span: Any
    ) {
        pattern.findAll(spannable.toString()).forEach { matchResult ->
            val range = matchResult.range
            spannable.setSpan(
                span,
                range.start,
                range.endInclusive + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private fun handleImage(line: String) {
        val pattern = "!\\[.*?\\]\\((.*?)\\)".toRegex()
        val matchResult = pattern.find(line)
        val imageUrl = matchResult?.groupValues?.get(1) ?: return

        val imageView = ImageView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                height = 300
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setPadding(0, 16, 0, 16)
            adjustViewBounds = true
        }

        binding.markdownContentContainer.addView(imageView)
        loadImage(imageUrl, imageView)
    }

    private fun loadImage(url: String, imageView: ImageView) {
        imageCache[url]?.let {
            imageView.setImageBitmap(it)
            return
        }

        Thread {
            try {
                val connection = URL(url).openConnection()
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                val inputStream = connection.getInputStream()
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                imageCache[url] = bitmap

                runOnUiThread {
                    imageView.setImageBitmap(bitmap)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Ошибка загрузки изображения: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }.start()
    }

    private fun handleTable(lines: List<String>, startIndex: Int): Int {
        val tableLayout = TableLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 16, 0, 16)
        }

        val headerRow = TableRow(this)
        lines[startIndex].split("|")
            .filter { it.isNotBlank() }
            .forEach { headerText ->
                headerRow.addView(TextView(this).apply {
                    text = headerText.trim()
                    setTypeface(null, Typeface.BOLD)
                    setPadding(8, 4, 8, 4)
                })
            }
        tableLayout.addView(headerRow)

        var currentIndex = startIndex + 2

        while (currentIndex < lines.size && lines[currentIndex].contains("|")) {
            val dataRow = TableRow(this)
            lines[currentIndex].split("|")
                .filter { it.isNotBlank() }
                .forEach { cellText ->
                    dataRow.addView(TextView(this).apply {
                        text = cellText.trim()
                        setPadding(8, 4, 8, 4)
                    })
                }
            tableLayout.addView(dataRow)
            currentIndex++
        }

        binding.markdownContentContainer.addView(tableLayout)
        return currentIndex - 1
    }

    private fun addEmptySpace() {
        View(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                16
            )
        }.also { binding.markdownContentContainer.addView(it) }
    }
}