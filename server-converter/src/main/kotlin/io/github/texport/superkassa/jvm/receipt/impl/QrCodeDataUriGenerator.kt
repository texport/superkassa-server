package io.github.texport.superkassa.jvm.receipt.impl

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kz.mybrain.superkassa.core.domain.port.QrCodeGeneratorPort
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO

/**
 * Генерация QR-кода в формате Data URI.
 */
object QrCodeDataUriGenerator : QrCodeGeneratorPort {

    init {
        // Гарантируем работу AWT в безголовом (headless) режиме на Linux-серверах
        System.setProperty("java.awt.headless", "true")
    }

    override fun generatePngDataUri(text: String, sizePx: Int): String? {
        if (text.isBlank()) return null
        return runCatching {
            val hints = mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
            )
            val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)

            // Создаем BufferedImage вручную без использования J2SE MatrixToImageWriter,
            // чтобы минимизировать внешние зависимости, хотя BufferedImage все еще из java.awt
            val width = matrix.width
            val height = matrix.height
            val image = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    val color = if (matrix.get(x, y)) 0x000000 else 0xFFFFFF
                    image.setRGB(x, y, color)
                }
            }

            val baos = ByteArrayOutputStream()
            ImageIO.write(image, "PNG", baos)
            "data:image/png;base64,${Base64.getEncoder().encodeToString(baos.toByteArray())}"
        }.getOrNull()
    }
}
