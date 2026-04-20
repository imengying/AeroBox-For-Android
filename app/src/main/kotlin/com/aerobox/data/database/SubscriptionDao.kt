package com.aerobox.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aerobox.data.model.Subscription
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY createdAt DESC")
    fun getAllSubscriptions(): Flow<List<Subscription>>

    @Query("SELECT * FROM subscriptions WHERE url = '' ORDER BY createdAt DESC")
    fun getLocalGroups(): Flow<List<Subscription>>

    @Query("SELECT * FROM subscriptions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Subscription?

    @Query("SELECT * FROM subscriptions WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<Subscription?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(subscription: Subscription): Long

    @Update
    suspend fun update(subscription: Subscription)

    @Query("UPDATE subscriptions SET nodeCount = :nodeCount WHERE id = :id")
    suspend fun updateNodeCount(id: Long, nodeCount: Int)

    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
