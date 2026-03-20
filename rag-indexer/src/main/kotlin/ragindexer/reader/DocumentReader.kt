package ragindexer.reader

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File

/**
 * Reads supported document formats and returns plain text content.
 * Supported extensions: txt, md, pdf, doc, docx.
 */
object DocumentReader {

    fun read(file: File): String {
        require(file.exists()) { "File not found: ${file.absolutePath}" }
        return when (file.extension.lowercase()) {
            "txt", "md" -> file.readText(Charsets.UTF_8)
            "pdf"       -> readPdf(file)
            "doc"       -> readDoc(file)
            "docx"      -> readDocx(file)
            else -> throw IllegalArgumentException(
                "Unsupported file type '.${file.extension}'. Supported: txt, md, pdf, doc, docx"
            )
        }
    }

    private fun readPdf(file: File): String =
        PDDocument.load(file).use { doc -> PDFTextStripper().getText(doc) }

    private fun readDoc(file: File): String =
        HWPFDocument(file.inputStream()).use { doc -> WordExtractor(doc).text }

    private fun readDocx(file: File): String =
        XWPFDocument(file.inputStream()).use { doc ->
            doc.paragraphs.joinToString("\n") { it.text }
        }
}
