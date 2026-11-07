package com.unicornops.seefi.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A photo previously received and saved by the app. */
data class ReceivedPhoto(
    val uri: Uri,
    val filename: String,
    val receivedAt: Long,
    val sizeBytes: Long,
)

/**
 * Saves extracted photos into the phone's media collection
 * (Pictures/Seefi/YYYY-MM-DD) via MediaStore, so they are visible to
 * the system gallery and other apps, and lists them back for the
 * in-app gallery.
 */
class PhotoStore(private val context: Context) {

    private val imageCollection: Uri =
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    /**
     * Saves one photo. Returns the MediaStore URI, or null when the
     * file could not be stored.
     */
    fun savePhoto(filename: String, data: ByteArray, receivedAtMillis: Long = System.currentTimeMillis()): Uri? {
        val resolver = context.contentResolver
        val extension = filename.substringAfterLast('.', "").lowercase().ifBlank { "jpg" }
        val mimeType = mimeTypeFor(extension) ?: return null
        val dateFolder = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(receivedAtMillis))

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "${System.currentTimeMillis()}_$filename")
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Seefi/$dateFolder")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = resolver.insert(imageCollection, values) ?: return null

        val written = runCatching {
            resolver.openOutputStream(uri)?.use { output ->
                output.write(data)
                output.flush()
                true
            } ?: false
        }.getOrDefault(false)

        if (!written) {
            runCatching { resolver.delete(uri, null, null) }
            return null
        }

        val finalizeValues = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        resolver.update(uri, finalizeValues, null, null)
        return uri
    }

    /** Lists every photo in the app's media folder, newest first. */
    fun queryPhotos(): List<ReceivedPhoto> {
        val resolver = context.contentResolver
        val photos = ArrayList<ReceivedPhoto>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE,
        )

        resolver.query(
            imageCollection,
            projection,
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
            arrayOf("Pictures/Seefi/%"),
            "${MediaStore.MediaColumns.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            while (cursor.moveToNext()) {
                photos.add(
                    ReceivedPhoto(
                        uri = Uri.withAppendedPath(imageCollection, cursor.getLong(idColumn).toString()),
                        filename = cursor.getString(nameColumn) ?: "",
                        receivedAt = cursor.getLong(dateColumn) * 1000,
                        sizeBytes = cursor.getLong(sizeColumn),
                    ),
                )
            }
        }
        return photos
    }

    fun deletePhoto(uri: Uri): Boolean =
        runCatching { context.contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)

    private fun mimeTypeFor(extension: String): String? = when (extension) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "raw", "cr2", "nef", "arw", "dng" -> "image/x-$extension"
        else -> null
    }
}
