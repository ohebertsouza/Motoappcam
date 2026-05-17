package com.hebert.motocamera.processing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

object BitmapUtils {

    fun fromImageProxy(image: ImageProxy): Bitmap {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
    }

    fun adjustExposure(src: Bitmap, factor: Float): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val lut = IntArray(256) { i -> (i * factor).toInt().coerceIn(0, 255) }
        for (i in px.indices) {
            px[i] = Color.rgb(
                lut[Color.red(px[i])],
                lut[Color.green(px[i])],
                lut[Color.blue(px[i])]
            )
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }
}
