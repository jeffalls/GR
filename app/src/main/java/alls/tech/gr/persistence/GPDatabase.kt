package alls.tech.gr.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import alls.tech.gr.persistence.converters.DateConverter
import alls.tech.gr.persistence.dao.AlbumDao
import alls.tech.gr.persistence.dao.SavedPhraseDao
import alls.tech.gr.persistence.dao.TeleportLocationDao
import alls.tech.gr.persistence.model.AlbumContentEntity
import alls.tech.gr.persistence.model.AlbumEntity
import alls.tech.gr.persistence.model.SavedPhraseEntity
import alls.tech.gr.persistence.model.TeleportLocationEntity

@Database(
    entities = [
        AlbumEntity::class,
        AlbumContentEntity::class,
        TeleportLocationEntity::class,
        SavedPhraseEntity::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(DateConverter::class)
abstract class GPDatabase : RoomDatabase() {
    abstract fun albumDao(): AlbumDao
    abstract fun teleportLocationDao(): TeleportLocationDao
    abstract fun savedPhraseDao(): SavedPhraseDao

    companion object {
        private const val DATABASE_NAME = "GR.db"

        fun create(context: Context): GPDatabase {
            return Room.databaseBuilder(context, GPDatabase::class.java, DATABASE_NAME)
                .fallbackToDestructiveMigration(false)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
        }
    }
}