package com.unicornops.seefi.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Verifies PhotoStore's MediaStore contract on a Robolectric runtime.
 *
 * Robolectric's MediaStore shadow accepts inserts (and generates real
 * content URIs) but does not serve query results, so saving is verified
 * against the recorded insert/update/delete statements - the exact
 * contract the ROM's MediaProvider fulfils on a real device.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PhotoStoreTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val store = PhotoStore(context)
    private val resolver get() = context.contentResolver

    @Test
    fun `saving a jpeg writes it under Pictures Seefi for today's date`() {
        val jpeg = ByteArray(64) { (it * 3).toByte() }

        val uri = store.savePhoto("IMG_0001.jpg", jpeg)

        assertNotNull("MediaStore insert should succeed", uri)

        val shadow = Shadows.shadowOf(resolver)
        val insert = shadow.insertStatements.single()
        val imageCollection = android.provider.MediaStore.Images.Media.getContentUri(
            android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY,
        )
        assertEquals("insert targets the images collection", imageCollection, insert.uri)
        assertTrue("returned uri is a row inside the collection: $uri", uri.toString().startsWith("$imageCollection/"))
        val values = insert.contentValues
        assertTrue(
            "display name keeps the original filename",
            values.getAsString(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                .endsWith("IMG_0001.jpg"),
        )
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        assertEquals(
            "saved under the dated app folder",
            "Pictures/Seefi/$today",
            values.getAsString(android.provider.MediaStore.MediaColumns.RELATIVE_PATH),
        )
        assertEquals(
            "image/jpeg",
            values.getAsString(android.provider.MediaStore.MediaColumns.MIME_TYPE),
        )
        assertEquals(
            "inserted as pending while the bytes are written",
            1,
            values.getAsInteger(android.provider.MediaStore.MediaColumns.IS_PENDING),
        )

        val update = shadow.updateStatements.single()
        assertEquals(uri, update.uri)
        assertEquals(
            0,
            update.contentValues.getAsInteger(android.provider.MediaStore.MediaColumns.IS_PENDING),
        )
    }

    @Test
    fun `non-image files are rejected before reaching MediaStore`() {
        assertEquals(null, store.savePhoto("readme.txt", "hello".toByteArray()))
        assertEquals(0, Shadows.shadowOf(resolver).insertStatements.size)
    }

    @Test
    fun `unknown extensions default to jpg`() {
        val uri = store.savePhoto("IMG_0002", ByteArray(64))
        assertNotNull(uri)
        val values = Shadows.shadowOf(resolver).insertStatements.single().contentValues
        assertEquals(
            "image/jpeg",
            values.getAsString(android.provider.MediaStore.MediaColumns.MIME_TYPE),
        )
    }

    @Test
    fun `deleting a photo removes it from MediaStore`() {
        val uri = store.savePhoto("IMG_0003.png", ByteArray(64))!!
        assertTrue(store.deletePhoto(uri))
        assertEquals(uri, Shadows.shadowOf(resolver).deletedUris.single())
    }

    @Test
    fun `raw formats use a raw mime type`() {
        val uri = store.savePhoto("IMG_0004.cr2", ByteArray(64))
        assertNotNull(uri)
        val values = Shadows.shadowOf(resolver).insertStatements.last().contentValues
        assertEquals(
            "image/x-cr2",
            values.getAsString(android.provider.MediaStore.MediaColumns.MIME_TYPE),
        )
    }

    @Test
    fun `querying is safe when the collection is empty`() {
        // Robolectric's shadow answers queries with null; on a device this
        // lists the saved photos.
        assertEquals(emptyList<ReceivedPhoto>(), store.queryPhotos())
    }
}
