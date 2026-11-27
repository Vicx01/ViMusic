package it.vfsfitvnm.vimusic

import android.os.Parcel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic historical-schema fixtures only; never opens the production database. */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, DatabaseInitializer::class.java)

    private val manualMigrations = DatabaseInitializer.migrations()
    private val unknownBlob = byteArrayOf(0x7f, 0x00, 0x42, 0x33)

    /** Independently constructs the Parcel/Bundle representation used by the old converter. */
    private fun fixtureBytes(mediaId: String, title: String?): ByteArray {
        val metadata = MediaMetadata.Builder().setTitle(title).build()
        val item = MediaItem.Builder().setMediaId(mediaId).setMediaMetadata(metadata).build()
        val parcel = Parcel.obtain()
        return try {
            parcel.writeBundle(item.toBundle())
            parcel.marshall()
        } finally {
            parcel.recycle()
        }
    }

    private fun seed(db: SupportSQLiteDatabase, version: Int) {
        val songColumns = mutableListOf("id", "title", "durationText", "totalPlayTimeMs")
        val songValues = mutableListOf<Any?>("migration-song", "Migration title", "3:21", 1234L)
        if (version >= 3) {
            songColumns.add("lyrics")
            songValues.add("fixture fixed lyrics")
        }
        if (version >= 16) {
            songColumns.add("synchronizedLyrics")
            songValues.add("[00:01.00]fixture synced lyrics")
        }
        db.execSQL(
            "INSERT INTO Song (${songColumns.joinToString()}) VALUES (${songValues.joinToString { "?" }})",
            songValues.toTypedArray(),
        )
        db.execSQL("INSERT INTO Playlist (id, name) VALUES (1, 'Migration playlist')")
        val table = if (version <= 11) "SongInPlaylist" else "SongPlaylistMap"
        db.execSQL("INSERT INTO $table (songId, playlistId, position) VALUES ('migration-song', 1, 2)")
        if (version == 2 || version == 3 || version >= 6) {
            db.execSQL("INSERT INTO QueuedMediaItem (id, mediaItem, position) VALUES (?, ?, ?)",
                arrayOf(101L, fixtureBytes("legacy-null", null), null))
            db.execSQL("INSERT INTO QueuedMediaItem (id, mediaItem, position) VALUES (?, ?, ?)",
                arrayOf(102L, fixtureBytes("legacy-title", "Saved title"), 4567L))
            db.execSQL("INSERT INTO QueuedMediaItem (id, mediaItem, position) VALUES (?, ?, ?)",
                arrayOf(103L, unknownBlob, null))
        }
    }

    private fun verify(db: SupportSQLiteDatabase, from: Int, to: Int) {
        db.query("SELECT title, totalPlayTimeMs FROM Song WHERE id = 'migration-song'").use { cursor ->
            assertTrue("song was lost", cursor.moveToFirst())
            assertEquals("Migration title", cursor.getString(0))
            assertEquals(1234L, cursor.getLong(1))
        }
        db.query("SELECT name FROM Playlist WHERE id = 1").use { cursor ->
            assertTrue("playlist was lost", cursor.moveToFirst())
            assertEquals("Migration playlist", cursor.getString(0))
        }
        val table = if (to <= 11) "SongInPlaylist" else "SongPlaylistMap"
        db.query("SELECT position FROM $table WHERE songId = 'migration-song' AND playlistId = 1").use { cursor ->
            assertTrue("playlist membership was lost", cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
        }
        if (from >= 3) {
            val sql = if (to < 23) "SELECT lyrics FROM Song WHERE id = 'migration-song'"
                      else "SELECT fixed FROM Lyrics WHERE songId = 'migration-song'"
            db.query(sql).use { cursor ->
                assertTrue("fixed lyrics were lost", cursor.moveToFirst())
                assertEquals("fixture fixed lyrics", cursor.getString(0))
            }
        }
        if (from >= 16) {
            val sql = if (to < 23) "SELECT synchronizedLyrics FROM Song WHERE id = 'migration-song'"
                      else "SELECT synced FROM Lyrics WHERE songId = 'migration-song'"
            db.query(sql).use { cursor ->
                assertTrue("synchronized lyrics were lost", cursor.moveToFirst())
                assertEquals("[00:01.00]fixture synced lyrics", cursor.getString(0))
            }
        }
        if (from == 2 || from == 3 || from >= 6) {
            // In schema 4/5 the queue is stored verbatim in a temporary side
            // table, restored to the declared queue table at schema 6.
            val queueTable = if (to == 4 || to == 5) "_vimusic_queue_migration_backup" else "QueuedMediaItem"
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$queueTable'").use { cursor ->
                assertTrue("queue table was removed", cursor.moveToFirst())
            }
            db.query("SELECT mediaItem, position FROM $queueTable WHERE id = 103").use { cursor ->
                assertTrue("unknown queue blob was lost", cursor.moveToFirst())
                assertArrayEquals(unknownBlob, cursor.getBlob(0))
                assertTrue(cursor.isNull(1))
            }
            db.query("SELECT mediaItem, position FROM $queueTable WHERE id = 101").use { cursor ->
                assertTrue("nullable queue item was lost", cursor.moveToFirst())
                assertTrue("nullable queue position changed", cursor.isNull(1))
                val decoded = Converters.mediaItemFromByteArray(cursor.getBlob(0))
                assertNotNull("nullable queue item did not deserialize", decoded)
                assertEquals("legacy-null", decoded?.mediaId)
                assertNull(decoded?.mediaMetadata?.title)
            }
            db.query("SELECT mediaItem, position FROM $queueTable WHERE id = 102").use { cursor ->
                assertTrue("queue item was lost", cursor.moveToFirst())
                assertEquals(4567L, cursor.getLong(1))
                val decoded = Converters.mediaItemFromByteArray(cursor.getBlob(0))
                assertNotNull("queue item did not deserialize", decoded)
                assertEquals("legacy-title", decoded?.mediaId)
                assertEquals("Saved title", decoded?.mediaMetadata?.title.toString())
            }
        }
    }

    private fun audit(toCurrent: Boolean) {
        val failures = mutableListOf<String>()
        for (from in 1..22) {
            val to = if (toCurrent) 23 else from + 1
            val name = "room-migration-test-${UUID.randomUUID()}.db"
            try {
                helper.createDatabase(name, from).use { seed(it, from) }
                helper.runMigrationsAndValidate(name, to, to != 4, *manualMigrations).use { verify(it, from, to) }
            } catch (failure: AssertionError) {
                failures.add("$from->$to: ${failure.message}")
            } catch (failure: Exception) {
                failures.add("$from->$to: ${failure.javaClass.simpleName}: ${failure.message}")
            } finally {
                instrumentation.targetContext.deleteDatabase(name)
            }
        }
        assertTrue("Historical migration failures:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    @Test
    fun roomProductionMigrationRegistrationPreservesLegacyAndUnknownBlobs() {
        for (from in 2..3) {
            val name = "room-production-path-${UUID.randomUUID()}.db"
            try {
                helper.createDatabase(name, from).use { seed(it, from) }
                val db = Room.databaseBuilder(instrumentation.targetContext, DatabaseInitializer::class.java, name)
                    .addMigrations(*DatabaseInitializer.migrations())
                    .allowMainThreadQueries()
                    .build()
                try {
                    verify(db.openHelper.writableDatabase, from, 23)
                    // The invalid blob cannot be decoded by the current converter.
                    // A failed read must not mutate or discard any persisted rows.
                    var readFailed = false
                    try {
                        db.database.queue()
                    } catch (expected: RuntimeException) {
                        readFailed = true
                    }
                    assertTrue("unknown blob unexpectedly decoded", readFailed)
                    verify(db.openHelper.writableDatabase, from, 23)
                    db.openHelper.writableDatabase.query("SELECT name FROM sqlite_master WHERE type='table' AND name='_vimusic_queue_migration_backup'").use { cursor ->
                        assertTrue("backup table should be removed after restoration", !cursor.moveToFirst())
                    }
                } finally {
                    db.close()
                }
            } finally {
                instrumentation.targetContext.deleteDatabase(name)
            }
        }
    }

    @Test
    fun everyHistoricalAdjacentMigrationPreservesFixtures() = audit(toCurrent = false)

    @Test
    fun everyHistoricalSchemaMigratesToCurrentWithFixtures() = audit(toCurrent = true)
}
