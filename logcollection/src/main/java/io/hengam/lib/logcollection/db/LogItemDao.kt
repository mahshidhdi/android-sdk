package io.hengam.lib.logcollection.db

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Delete
import android.arch.persistence.room.Insert
import android.arch.persistence.room.OnConflictStrategy.REPLACE
import android.arch.persistence.room.Query


@Dao
interface LogItemDao {
    @Insert(onConflict = REPLACE)
    fun insert(vararg entities: LogEntity?)

    @Delete
    fun delete(vararg entities: LogEntity)

    @Query("SELECT * FROM logs WHERE isSent=0 LIMIT :limit")
    fun getNewLogs(limit: Int): List<LogEntity>

    @Query("SELECT COUNT(*) FROM logs WHERE isSent=0")
    fun getNewLogsCount(): Int

    @Query("DELETE FROM logs WHERE :now - time > :expireDuration OR isSent=1")
    fun deleteExpiredLogs(now: Long, expireDuration: Long)
}