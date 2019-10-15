package io.hengam.lib.logcollection.db

import android.arch.persistence.room.Database
import android.arch.persistence.room.Room
import android.arch.persistence.room.RoomDatabase
import android.arch.persistence.room.TypeConverters
import android.content.Context
import io.hengam.lib.internal.ioThread
import io.hengam.lib.logcollection.Constants.SYNC_CHUNK_COUNT
import io.hengam.lib.logcollection.dagger.LogCollectionScope
import io.hengam.lib.utils.TimeUtils
import io.reactivex.Completable
import io.reactivex.Single
import javax.inject.Inject


@Database(entities = [LogEntity::class], version = 1)
@TypeConverters(ListConverter::class, MapConverter::class, LogErrorConverter::class)
abstract class LogCollectionDatabase : RoomDatabase() {
    abstract val logItemDAO: LogItemDao
}

@LogCollectionScope
class LogCollectionDatabaseImpl @Inject constructor(
    context: Context
) {
    private val database: LogCollectionDatabase =
        Room.databaseBuilder(context, LogCollectionDatabase::class.java, DATABASE_NAME).build()

    fun insertLogs(log: LogEntity?): Completable {
        return Completable.fromCallable {
            database.logItemDAO.insert(log)
        }.subscribeOn(ioThread())
    }

    fun getNewLogs() = Single.fromCallable{
        database.logItemDAO.getNewLogs(SYNC_CHUNK_COUNT)
    }.subscribeOn(ioThread())

    fun getLogsCount() = Single.fromCallable{
        database.logItemDAO.getNewLogsCount()
    }.subscribeOn(ioThread())

    fun clearDatabase(): Completable {
        return Completable.fromCallable { database.clearAllTables() }
            .subscribeOn(ioThread())
    }

    fun cleanDatabase(): Completable {
        return Completable.fromCallable { database.logItemDAO.deleteExpiredLogs(TimeUtils.nowMillis(), LOG_EXPIRATION_DURATION) }
            .subscribeOn(ioThread())
    }

    fun updateDatabase(logs: List<LogEntity>): Completable{
        return Completable.fromCallable { database.logItemDAO.insert(*logs.toTypedArray()) }
            .subscribeOn(ioThread())
    }

    companion object {
        private const val DATABASE_NAME = "hengam_logs.db"
        private const val LOG_EXPIRATION_DURATION = 5 * 24 * 3600 * 1000L
    }
}