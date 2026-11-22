package pl.szczodrzynski.edziennik.data.enums

import android.graphics.Bitmap
import android.os.Build
import java.io.OutputStream

enum class TimetableImageFormat(val extension: String) {
    PNG("png"),
    WEBP("webp");

    val mimeType: String
        get() = "image/$extension"

    @Suppress("DEPRECATION")
    fun compress(bitmap: Bitmap, output: OutputStream): Boolean = when (this) {
        PNG -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 75, output)
        } else {
            bitmap.compress(Bitmap.CompressFormat.WEBP, 100, output)
        }
    }
}
