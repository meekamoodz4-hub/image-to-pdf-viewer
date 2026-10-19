
package com.meekamoodz.imagepdfviewer

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val pickImagesRequest = 100
    private val savePdfRequest = 101

    private val selectedImages = mutableListOf<Uri>()
    private lateinit var statusText: TextView
    private lateinit var createButton: Button
    private var pdfDocument: PdfDocument? = null
    private var lastSavedPdf: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 36, 28, 28)
            setBackgroundColor(Color.rgb(245, 247, 252))
        }

        val title = TextView(this).apply {
            text = "Image to PDF Viewer"
            textSize = 27f
            setTextColor(Color.rgb(35, 55, 100))
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "Turn your pictures into PDF files"
            textSize = 16f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 30)
        }

        val chooseButton = Button(this).apply {
            text = "Select Pictures"
            setOnClickListener { chooseImages() }
        }

        statusText = TextView(this).apply {
            text = "No pictures selected yet"
            textSize = 16f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 22, 0, 22)
        }

        createButton = Button(this).apply {
            text = "Convert and Save PDF"
            isEnabled = false
            setOnClickListener { startPdfCreation() }
        }

        val openButton = Button(this).apply {
            text = "Open Last Saved PDF"
            setOnClickListener { openLastPdf() }
        }

        val note = TextView(this).apply {
            text = "Choose one or more pictures, then convert them into a single PDF. You can choose where to save the file."
            textSize = 14f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 0)
        }

        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(chooseButton, buttonParams())
        layout.addView(statusText)
        layout.addView(createButton, buttonParams())
        layout.addView(openButton, buttonParams())
        layout.addView(note)

        setContentView(layout)
    }

    private fun buttonParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = 12
        }
    }

    private fun chooseImages() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }

        startActivityForResult(intent, pickImagesRequest)
    }

    @Deprecated("Uses the compatible Android activity result method")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != RESULT_OK || data == null) return

        if (requestCode == pickImagesRequest) {
            selectedImages.clear()

            data.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) {
                    selectedImages.add(clip.getItemAt(i).uri)
                }
            } ?: data.data?.let { uri ->
                selectedImages.add(uri)
            }

            statusText.text = if (selectedImages.isEmpty()) {
                "No pictures selected"
            } else {
                "${selectedImages.size} picture(s) selected"
            }

            createButton.isEnabled = selectedImages.isNotEmpty()
        }

        if (requestCode == savePdfRequest) {
            val destination = data.data ?: return
            savePdfTo(destination)
        }
    }

    private fun startPdfCreation() {
        if (selectedImages.isEmpty()) {
            Toast.makeText(this, "Select pictures first", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
            putExtra(Intent.EXTRA_TITLE, "MyPictures.pdf")
        }

        startActivityForResult(intent, savePdfRequest)
    }

    private fun savePdfTo(destination: Uri) {
        createButton.isEnabled = false
        statusText.text = "Creating your PDF..."

        Thread {
            var document: PdfDocument? = null

            try {
                document = PdfDocument()

                selectedImages.forEachIndexed { index, uri ->
                    val bitmap = contentResolver.openInputStream(uri).use { stream ->
                        BitmapFactory.decodeStream(stream)
                    } ?: throw Exception("Could not read picture ${index + 1}")

                    try {
                        val pageWidth = 595
                        val pageHeight = 842
                        val margin = 28f

                        val pageInfo = PdfDocument.PageInfo.Builder(
                            pageWidth,
                            pageHeight,
                            index + 1
                        ).create()

                        val page = document.startPage(pageInfo)

                        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                        val available = RectF(
                            margin,
                            margin,
                            pageWidth - margin,
                            pageHeight - margin
                        )

                        val scale = minOf(
                            available.width() / bitmap.width,
                            available.height() / bitmap.height
                        )

                        val drawWidth = bitmap.width * scale
                        val drawHeight = bitmap.height * scale

                        val left = (pageWidth - drawWidth) / 2f
                        val top = (pageHeight - drawHeight) / 2f

                        page.canvas.drawColor(Color.WHITE)
                        page.canvas.drawBitmap(
                            bitmap,
                            null,
                            RectF(left, top, left + drawWidth, top + drawHeight),
                            paint
                        )

                        document.finishPage(page)
                    } finally {
                        bitmap.recycle()
                    }
                }

                contentResolver.openOutputStream(destination, "w").use { output ->
                    if (output == null) throw Exception("Cannot open save location")
                    document.writeTo(output)
                }

                runOnUiThread {
                    lastSavedPdf = destination
                    statusText.text = "PDF saved successfully!"
                    Toast.makeText(
                        this,
                        "Your PDF has been saved",
                        Toast.LENGTH_LONG
                    ).show()
                    createButton.isEnabled = selectedImages.isNotEmpty()
                    openLastPdf()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "Could not create PDF. Please try again."
                    Toast.makeText(
                        this,
                        e.message ?: "An error occurred",
                        Toast.LENGTH_LONG
                    ).show()
                    createButton.isEnabled = selectedImages.isNotEmpty()
                }
            } finally {
                document?.close()
            }
        }.start()
    }

    private fun openLastPdf() {
        val uri = lastSavedPdf ?: run {
            Toast.makeText(this, "Save a PDF first", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "PDF saved. Install a PDF viewer to open it.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onDestroy() {
        pdfDocument?.close()
        super.onDestroy()
    }
}
