package com.pat.app.holidayplanner.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "PhotoScaler"

/** Plenty for a phone screen, and small enough that the backend will accept it as text. */
private const val MAX_EDGE_PX = 1600
private const val JPEG_QUALITY = 85

/** Sampling can only halve, so each step doubles the divisor. */
private const val HALVING = 2

private const val BYTES_PER_KB = 1024

/** What a scaled photo weighs in the end, roughly, once base64 has added its third. */
data class ScaledPhoto(val bytes: ByteArray, val mimeType: String = "image/jpeg") {
    // Arrays compare by identity, so the generated equals would be wrong rather than merely odd.
    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)
}

/**
 * Turns a shared photo into something worth sending.
 *
 * A picture from the camera is several megabytes and a third larger again as base64, which the
 * backend refuses; it is also far more than any phone screen shows. Decoding through
 * [ImageDecoder] rather than by hand also applies the orientation the camera recorded, so a
 * photo taken sideways is not stored sideways.
 */
class PhotoScaler(private val context: Context) {

    suspend fun scale(uri: Uri): Result<ScaledPhoto> = withContext(Dispatchers.IO) {
        try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                // Software, because a hardware bitmap cannot be read back out again to compress.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
                val longest = maxOf(info.size.width, info.size.height)
                decoder.setTargetSampleSize(sampleSizeFor(longest))
            }
            val bitmap = decoded.fitted()
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            bitmap.recycle()
            if (bitmap !== decoded) decoded.recycle()
            Log.d(TAG, "scaled $uri to ${out.size() / BYTES_PER_KB} KB")
            Result.success(ScaledPhoto(out.toByteArray()))
        } catch (e: IOException) {
            Log.w(TAG, "could not read $uri: ${e.message}")
            Result.failure(e)
        } catch (e: IllegalArgumentException) {
            // Not an image, or one this device cannot decode.
            Log.w(TAG, "could not decode $uri: ${e.message}")
            Result.failure(e)
        }
    }

    /** Sampling halves the picture at a time, so this is the last power of two that stays big enough. */
    private fun sampleSizeFor(longestEdge: Int): Int {
        var sample = 1
        while (longestEdge / (sample * HALVING) >= MAX_EDGE_PX) sample *= HALVING
        return sample
    }

    /** Sampling only gets within a factor of two, so the rest of the way is an exact scale. */
    private fun Bitmap.fitted(): Bitmap {
        val longest = maxOf(width, height)
        if (longest <= MAX_EDGE_PX) return this
        val ratio = MAX_EDGE_PX.toFloat() / longest
        return Bitmap.createScaledBitmap(this, (width * ratio).toInt(), (height * ratio).toInt(), true)
    }
}
