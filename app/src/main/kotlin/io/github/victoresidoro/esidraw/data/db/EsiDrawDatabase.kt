package io.github.victoresidoro.esidraw.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [EventEntity::class, ParticipantEntity::class, RuleEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class EsiDrawDatabase : RoomDatabase() {

    abstract fun dao(): EsiDrawDao

    companion object {
        fun create(context: Context): EsiDrawDatabase =
            Room.databaseBuilder(context, EsiDrawDatabase::class.java, "esidraw.db").build()
    }
}
