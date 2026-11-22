/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.azx8788a.pan123.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

object TransferStatus {
    const val QUEUED = "queued"
    const val RUNNING = "running"
    const val PAUSED = "paused"
    const val CANCELLED = "cancelled"
    const val FAILED = "failed"
    const val COMPLETE = "complete"
}

@Entity(tableName = "transfers", indices = [Index("accountKey")])
data class TransferEntity(
    @PrimaryKey val id: String,
    val accountKey: String,
    val kind: String,
    val name: String,
    val localPath: String,
    val cloudJson: String = "",
    val parentId: Long = 0,
    val status: String = TransferStatus.QUEUED,
    val done: Long = 0,
    val total: Long = 0,
    val message: String = "等待网络或传输队列",
    val uploadJson: String = "",
    val workId: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface TransferDao {
    @Query("SELECT * FROM transfers WHERE accountKey = :accountKey ORDER BY createdAt DESC")
    fun observe(accountKey: String): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfers WHERE id = :id")
    suspend fun get(id: String): TransferEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(task: TransferEntity): Long

    @Query("UPDATE transfers SET status=:status, message=:message WHERE id=:id AND workId=:workId AND status IN ('queued','running')")
    suspend fun workerStatus(id: String, workId: String, status: String, message: String): Int

    @Query("UPDATE transfers SET done=:done, total=:total, message=:message WHERE id=:id AND workId=:workId AND status IN ('queued','running')")
    suspend fun progress(id: String, workId: String, done: Long, total: Long, message: String)

    @Query("UPDATE transfers SET uploadJson=:json WHERE id=:id AND workId=:workId AND status IN ('queued','running')")
    suspend fun uploadState(id: String, workId: String, json: String)

    @Query("UPDATE transfers SET status=:status, message=:message WHERE id=:id AND status IN ('queued','running','paused','failed')")
    suspend fun control(id: String, status: String, message: String): Int

    @Query("UPDATE transfers SET workId=:workId, status='queued', message='等待网络或传输队列' WHERE id=:id AND status IN ('paused','failed','cancelled')")
    suspend fun reschedule(id: String, workId: String): Int

    @Query("UPDATE transfers SET status='paused', message='已暂停' WHERE accountKey=:accountKey AND status IN ('queued','running')")
    suspend fun pauseAccount(accountKey: String)

    @Query("DELETE FROM transfers WHERE id=:id AND status IN ('complete','cancelled','failed','paused')")
    suspend fun delete(id: String)

    @Query("DELETE FROM transfers WHERE accountKey=:accountKey AND status IN ('complete','cancelled')")
    suspend fun clearFinished(accountKey: String)
}

@Database(entities = [TransferEntity::class], version = 1, exportSchema = true)
abstract class TransferDatabase : RoomDatabase() {
    abstract fun transfers(): TransferDao
}
