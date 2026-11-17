package it.labirintodigitale.bcc.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import it.labirintodigitale.bcc.R

private val ZXING_FORMATS = mapOf(
    "code128" to BarcodeFormat.CODE_128,
    "ean13" to BarcodeFormat.EAN_13,
    "ean8" to BarcodeFormat.EAN_8,
    "upca" to BarcodeFormat.UPC_A,
    "upce" to BarcodeFormat.UPC_E,
    "code39" to BarcodeFormat.CODE_39,
    "code93" to BarcodeFormat.CODE_93,
    "interleaved2of5" to BarcodeFormat.ITF,
    "itf14" to BarcodeFormat.ITF,
    "rationalizedCodabar" to BarcodeFormat.CODABAR,
    "qrcode" to BarcodeFormat.QR_CODE,
    "pdf417" to BarcodeFormat.PDF_417,
    "azteccode" to BarcodeFormat.AZTEC,
    "datamatrix" to BarcodeFormat.DATA_MATRIX,
)

private val TWO_D = setOf("qrcode", "azteccode", "datamatrix")

/** Encode [number] as [format]; null if the format is "none" or the number is not valid for it. */
fun encodeBarcode(format: String, number: String): BitMatrix? {
    val zx = ZXING_FORMATS[format] ?: return null
    var text = number.trim()
    if (text.isEmpty()) return null
    if (format == "itf14" && text.length == 13 && text.all(Char::isDigit)) text += gs1CheckDigit(text)
    if (format in setOf("interleaved2of5", "itf14") && text.length % 2 == 1) return null
    val hints = mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.CHARACTER_SET to "UTF-8")
    return runCatching { MultiFormatWriter().encode(text, zx, 0, 0, hints) }.getOrNull()
}

private fun gs1CheckDigit(digits: String): Char {
    var sum = 0
    digits.reversed().forEachIndexed { i, c -> sum += (c - '0') * if (i % 2 == 0) 3 else 1 }
    return '0' + (10 - sum % 10) % 10
}

/** Big barcode on a white background, crisp at any size, with the number below. */
@Composable
fun BarcodeView(format: String, number: String, modifier: Modifier = Modifier) {
    val matrix = remember(format, number) { encodeBarcode(format, number) }
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (matrix != null) {
            val twoD = format in TWO_D
            val stacked = twoD || format == "pdf417"
            val ratio = if (stacked) matrix.width.toFloat() / matrix.height else 2.6f
            Box(
                Modifier
                    .fillMaxWidth(if (twoD) 0.75f else 1f)
                    .aspectRatio(ratio)
            ) {
                Canvas(Modifier.matchParentSize()) {
                    val mw = size.width / matrix.width
                    val mh = size.height / matrix.height
                    for (y in 0 until matrix.height) {
                        var x = 0
                        while (x < matrix.width) {
                            if (!matrix.get(x, y)) { x++; continue }
                            val start = x
                            while (x < matrix.width && matrix.get(x, y)) x++
                            drawRect(
                                Color.Black,
                                topLeft = Offset(start * mw, y * mh),
                                // Slight overlap avoids hairline gaps between modules.
                                size = Size((x - start) * mw + 0.5f, mh + 0.5f),
                            )
                        }
                    }
                }
            }
        }
        if (number.isNotBlank()) {
            Text(
                number,
                color = Color.Black,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                fontSize = if (matrix == null) 32.sp else 20.sp,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (matrix == null && format != "none" && number.isNotBlank()) {
            Text(
                stringResource(R.string.invalid_code),
                color = Color(0xFFB00020),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
