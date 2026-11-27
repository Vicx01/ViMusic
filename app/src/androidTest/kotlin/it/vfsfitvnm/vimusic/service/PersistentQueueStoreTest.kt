package it.vfsfitvnm.vimusic.service

import androidx.media3.common.MediaItem
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.vfsfitvnm.vimusic.DatabaseInitializer
import it.vfsfitvnm.vimusic.models.QueuedMediaItem
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistentQueueStoreTest {
    private fun item(id: String) = QueuedMediaItem(mediaItem = MediaItem.Builder().setMediaId(id).build(), position = null)

    private fun withDatabase(block: (DatabaseInitializer) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "queue-safety-${UUID.randomUUID()}.db"
        val db = Room.databaseBuilder(context, DatabaseInitializer::class.java, name)
            .allowMainThreadQueries()
            .build()
        try {
            block(db)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun unreadableQueueCannotBeOverwritten() = withDatabase { db ->
        val blob = byteArrayOf(0x7f, 0x00, 0x42, 0x33)
        db.openHelper.writableDatabase.execSQL("INSERT INTO QueuedMediaItem (id, mediaItem, position) VALUES (?, ?, ?)", arrayOf(1L, blob, null))
        val store = PersistentQueueStore(db)
        var failed = false
        try { store.read() } catch (expected: RuntimeException) { failed = true }
        assertTrue("invalid blob should fail restoration", failed)
        assertFalse(store.replaceIfRestored(listOf(item("new"))))
        db.openHelper.writableDatabase.query("SELECT mediaItem FROM QueuedMediaItem WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertArrayEquals(blob, cursor.getBlob(0))
        }
    }

    @Test
    fun queueReplacementRollsBackOnInsertFailure() = withDatabase { db ->
        db.database.insert(listOf(item("saved")))
        val store = PersistentQueueStore(db)
        assertEquals("saved", store.read().single().mediaItem.mediaId)
        store.markRestored()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER refuse_queue_insert BEFORE INSERT ON QueuedMediaItem BEGIN SELECT RAISE(ABORT, 'test insert failure'); END")
        var failed = false
        try { store.replaceIfRestored(listOf(item("new"))) } catch (expected: RuntimeException) { failed = true }
        assertTrue("insert failure should propagate", failed)
        assertEquals("saved", store.read().single().mediaItem.mediaId)
    }

    @Test
    fun enablingPersistenceOnlyPermitsEmptyOrRestoredQueue() = withDatabase { db ->
        val store = PersistentQueueStore(db)
        assertTrue(store.allowSavingIfEmpty())
        assertTrue(store.replaceIfRestored(listOf(item("saved"))))
        val newSession = PersistentQueueStore(db)
        assertFalse(newSession.allowSavingIfEmpty())
        assertFalse(newSession.replaceIfRestored(listOf(item("replacement"))))
        assertEquals("saved", newSession.read().single().mediaItem.mediaId)
        newSession.markRestored()
        assertTrue(newSession.replaceIfRestored(listOf(item("replacement"))))
        assertEquals("replacement", newSession.read().single().mediaItem.mediaId)
    }
}
