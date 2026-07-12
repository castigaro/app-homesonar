package com.castigaro.homesonar.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object Photos {

    private const val MAX_DIMENSION = 1280
    private const val JPEG_QUALITY = 80

    fun newPhotoFile(context: Context): File {
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        return File(dir, "photo_${System.currentTimeMillis()}.jpg")
    }

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "com.castigaro.homesonar.fileprovider", file)

    /**
     * Verkleinert das Foto auf max. [MAX_DIMENSION] Pixel Kantenlänge und
     * überschreibt die Datei. Spart Speicher und hält die API-Requests klein.
     */
    fun downscale(file: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return

        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAX_DIMENSION) {
            sampleSize *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return

        val scale = MAX_DIMENSION.toFloat() / maxOf(bitmap.width, bitmap.height)
        val finalBitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            bitmap
        }

        FileOutputStream(file).use { out ->
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        if (finalBitmap !== bitmap) finalBitmap.recycle()
        bitmap.recycle()
    }

    fun toBase64(file: File): String =
        Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)

    /**
     * Speichert den Ausschnitt [rect] (Bitmap-Pixelkoordinaten) von
     * [sourcePath] als neue Fotodatei. Die Quelldatei bleibt unverändert —
     * andere Gegenstände können sie weiter referenzieren.
     */
    fun cropToNewFile(context: Context, sourcePath: String, rect: Rect): File? {
        val source = BitmapFactory.decodeFile(sourcePath) ?: return null
        val bounds = Rect(rect)
        if (!bounds.intersect(0, 0, source.width, source.height) ||
            bounds.width() < 16 || bounds.height() < 16
        ) {
            source.recycle()
            return null
        }
        val crop = Bitmap.createBitmap(source, bounds.left, bounds.top, bounds.width(), bounds.height())
        val file = newPhotoFile(context)
        val ok = runCatching {
            FileOutputStream(file).use { out ->
                crop.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
        }.isSuccess
        if (crop !== source) crop.recycle()
        source.recycle()
        if (!ok) {
            runCatching { file.delete() }
            return null
        }
        return file
    }
}
