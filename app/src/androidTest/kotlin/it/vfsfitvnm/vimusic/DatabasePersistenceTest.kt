package it.vfsfitvnm.vimusic

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.vfsfitvnm.vimusic.models.QueuedMediaItem
import java.lang.reflect.InvocationTargetException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Uses a uniquely named test-only database, never the production data.db. */
@RunWith(AndroidJUnit4::class)
class DatabasePersistenceTest {
    @Test
    fun queuedMediaItemPersistsThroughExistingConvertersAfterReopen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "room-interop-test-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, DatabaseInitializer::class.java, name)
            .allowMainThreadQueries()
            .build()

        val item = MediaItem.Builder()
            .setMediaId("room-queue-fixture")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Fixture title").setArtist("Fixture artist").build())
            .build()
        try {
            val writer = open()
            try {
                writer.database.insert(listOf(QueuedMediaItem(mediaItem = item, position = 12345L)))
                assertEquals(1, writer.database.queue().size)
            } finally {
                writer.close()
            }
            val reader = open()
            try {
                val restored = reader.database.queue().single()
                assertEquals("room-queue-fixture", restored.mediaItem.mediaId)
                assertEquals("Fixture title", restored.mediaItem.mediaMetadata.title.toString())
                assertEquals("Fixture artist", restored.mediaItem.mediaMetadata.artist.toString())
                assertEquals(12345L, restored.position)
            } finally {
                reader.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun deferredDaoResolvesOnlyOnUseAndKeepsDaoBehavior() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "room-deferred-dao-${UUID.randomUUID()}.db"
        val db = Room.databaseBuilder(context, DatabaseInitializer::class.java, name)
            .allowMainThreadQueries()
            .build()
        try {
            var resolutions = 0
            val dao = deferredDatabase {
                resolutions++
                db.database
            }
            assertEquals(0, resolutions)
            dao.insert(listOf(QueuedMediaItem(mediaItem = MediaItem.Builder().setMediaId("deferred-fixture").build(), position = null)))
            assertEquals(1, resolutions)
            assertEquals("deferred-fixture", dao.queue().single().mediaItem.mediaId)
            assertEquals(2, resolutions)
            assertTrue(runBlocking { dao.favorites().first() }.isEmpty())
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_deferred_insert BEFORE INSERT ON QueuedMediaItem BEGIN SELECT RAISE(ABORT, 'test failure'); END")
            var thrown: Throwable? = null
            try {
                dao.insert(listOf(QueuedMediaItem(mediaItem = MediaItem.Builder().setMediaId("fail").build(), position = null)))
            } catch (failure: Throwable) {
                thrown = failure
            }
            assertNotNull("DAO failure must propagate", thrown)
            assertFalse("DAO failure must not be wrapped in InvocationTargetException", thrown is InvocationTargetException)
            assertEquals("deferred-fixture", dao.queue().single().mediaItem.mediaId)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun mediaItemConvertersHandleNullAndRoundTrip() {
        assertNull(Converters.mediaItemFromByteArray(null))
        assertNull(Converters.mediaItemToByteArray(null))
        val original = MediaItem.Builder().setMediaId("converter-fixture").build()
        val encoded = Converters.mediaItemToByteArray(original)
        assertNotNull(encoded)
        val restored = Converters.mediaItemFromByteArray(encoded)
        assertNotNull(restored)
        assertEquals("converter-fixture", restored?.mediaId)
    }
}
