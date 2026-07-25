package com.example.lightpdfeditor

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.InputStream

class MainActivity : AppCompatActivity() {

    private lateinit var ivPdfView: ImageView
    private lateinit var etInputText: EditText
    private lateinit var tvStatus: TextView
    private lateinit var btnSave: Button

    private var currentPdfUri: Uri? = null
    private var targetPdfX = 100f
    private var targetPdfY = 100f
    private var pdfPageWidth = 0
    private var pdfPageHeight = 0

    // Picker launcher to open PDF file
    private val openPdfLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            currentPdfUri = it
            renderPdfPage(it)
            btnSave.isEnabled = true
        }
    }

    // Saver launcher to write modified PDF file
    private val savePdfLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { destinationUri ->
            savePdfWithAddedText(destinationUri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize PDFBox lightweight engine
        PDFBoxResourceLoader.init(applicationContext)

        val btnOpen = findViewById<Button>(R.id.btnOpen)
        btnSave = findViewById(R.id.btnSave)
        etInputText = findViewById(R.id.etInputText)
        tvStatus = findViewById(R.id.tvStatus)
        ivPdfView = findViewById(R.id.ivPdfView)

        btnOpen.setOnClickListener {
            openPdfLauncher.launch(arrayOf("application/pdf"))
        }

        btnSave.setOnClickListener {
            val textToAdd = etInputText.text.toString()
            if (textToAdd.isEmpty()) {
                Toast.makeText(this, "Please enter text to add first!", Toast.LENGTH_SHORT).show()
            } else {
                savePdfLauncher.launch("edited_document.pdf")
            }
        }

        // Tap listener to choose text coordinate on the PDF
        ivPdfView.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_DOWN && pdfPageWidth > 0) {
                val viewWidth = view.width.toFloat()
                val viewHeight = view.height.toFloat()

                // Calculate relative PDF points based on tap position
                val touchX = event.x / viewWidth * pdfPageWidth
                val touchY = event.y / viewHeight * pdfPageHeight

                targetPdfX = touchX
                // Convert Android Y screen coordinates (Top->Bottom) to PDF Y coordinates (Bottom->Top)
                targetPdfY = pdfPageHeight - touchY

                tvStatus.text = "Position set at X: ${targetPdfX.toInt()}, Y: ${targetPdfY.toInt()}"
            }
            true
        }
    }

    // Pure Native Rendering without external bloat libraries
    private fun renderPdfPage(uri: Uri) {
        try {
            val pfd: ParcelFileDescriptor? = contentResolver.openFileDescriptor(uri, "r")
            pfd?.let {
                val pdfRenderer = PdfRenderer(it)
                if (pdfRenderer.pageCount > 0) {
                    val page = pdfRenderer.openPage(0) // Renders page 1
                    pdfPageWidth = page.width
                    pdfPageHeight = page.height

                    val bitmap = Bitmap.createBitmap(pdfPageWidth, pdfPageHeight, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    ivPdfView.setImageBitmap(bitmap)

                    page.close()
                    tvStatus.text = "Tap on the document to select text location."
                }
                pdfRenderer.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to load PDF preview", Toast.LENGTH_SHORT).show()
        }
    }

    // Embed new text on existing PDF and save
    private fun savePdfWithAddedText(saveDestinationUri: Uri) {
        val originalUri = currentPdfUri ?: return
        val textContent = etInputText.text.toString()

        try {
            val inputStream: InputStream? = contentResolver.openInputStream(originalUri)
            val document = PDDocument.load(inputStream)
            val page = document.getPage(0)

            // Open stream to append text to page
            val contentStream = PDPageContentStream(
                document, 
                page, 
                PDPageContentStream.AppendMode.APPEND, 
                true, 
                true
            )

            contentStream.beginText()
            contentStream.setFont(PDType1Font.HELVETICA_BOLD, 18f)
            contentStream.newLineAtOffset(targetPdfX, targetPdfY)
            contentStream.showText(textContent)
            contentStream.endText()
            contentStream.close()

            // Output edited PDF file
            contentResolver.openOutputStream(saveDestinationUri)?.use { outputStream ->
                document.save(outputStream)
            }
            document.close()

            Toast.makeText(this, "Saved Successfully!", Toast.LENGTH_LONG).show()
            tvStatus.text = "File saved successfully!"
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error saving PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}