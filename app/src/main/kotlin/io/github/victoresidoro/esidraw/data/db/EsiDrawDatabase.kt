package io.github.victoresidoro.esidraw.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        EventEntity::class,
        ParticipantEntity::class,
        RuleEntity::class,
        GroupEntity::class,
        GroupMemberEntity::class,
        DrawHistoryEntity::class,
        DrawHistoryEntryEntity::class,
    ],
    version = 3,
    // Sem exportação: as variantes debug e release do KSP escreviam o mesmo arquivo de esquema e o corrompiam.
    // As migrações são escritas à mão (ver MIGRATION_1_2) e conferidas na instalação por cima.
    exportSchema = false,
)
abstract class EsiDrawDatabase : RoomDatabase() {

    abstract fun dao(): EsiDrawDao

    companion object {
        /** 0.2.0: grupos salvos. O SQL espelha o que o Room gera, porque ele valida o esquema ao abrir. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `contact_group` " +
                        "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `contact_group_member` " +
                        "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `groupId` INTEGER NOT NULL, " +
                        "`name` TEXT NOT NULL, `phone` TEXT NOT NULL, " +
                        "FOREIGN KEY(`groupId`) REFERENCES `contact_group`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_contact_group_member_groupId` " +
                        "ON `contact_group_member` (`groupId`)",
                )
            }
        }

        /** 0.4.0: histórico de sorteios por data. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `draw_history` " +
                        "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "`guarantee` TEXT NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `draw_history_entry` " +
                        "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `drawId` INTEGER NOT NULL, " +
                        "`giverId` INTEGER NOT NULL, `giverName` TEXT NOT NULL, `receiverId` INTEGER NOT NULL, " +
                        "`receiverName` TEXT NOT NULL, FOREIGN KEY(`drawId`) REFERENCES `draw_history`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_draw_history_entry_drawId` " +
                        "ON `draw_history_entry` (`drawId`)",
                )
            }
        }

        fun create(context: Context): EsiDrawDatabase =
            Room.databaseBuilder(context, EsiDrawDatabase::class.java, "esidraw.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
