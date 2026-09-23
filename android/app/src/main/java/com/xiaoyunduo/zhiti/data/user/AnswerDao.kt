package com.xiaoyunduo.zhiti.data.user

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AnswerDao {
    @Query("SELECT * FROM answers WHERE qid = :qid")
    suspend fun get(qid: String): AnswerRecord?

    @Query("SELECT qid FROM answers WHERE attempts > 0")
    suspend fun attemptedIds(): List<String>


    @Query("SELECT qid FROM answers WHERE attempts > 0")
    fun attemptedIdsFlow(): Flow<List<String>>
    @Query("SELECT qid FROM answers WHERE everWrong = 1 AND corrected = 0 ORDER BY updatedAt DESC")
    suspend fun wrongIds(): List<String>

    @Query("SELECT qid FROM answers WHERE favorite = 1 ORDER BY updatedAt DESC")
    suspend fun favoriteIds(): List<String>

    @Query("SELECT COUNT(*) FROM answers WHERE attempts > 0")
    fun answeredCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM answers WHERE everWrong = 1 AND corrected = 0")
    fun wrongCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM answers WHERE favorite = 1")
    fun favoriteCount(): Flow<Int>

    @Upsert
    suspend fun upsert(record: AnswerRecord)

    @Query("DELETE FROM answers")
    suspend fun clear()
}
