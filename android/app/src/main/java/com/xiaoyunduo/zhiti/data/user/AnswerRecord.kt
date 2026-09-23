package com.xiaoyunduo.zhiti.data.user

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "answers")
data class AnswerRecord(
    @PrimaryKey val qid: String,
    val module: String,
    val selected: String,
    val correct: Boolean,
    val everWrong: Boolean,
    val corrected: Boolean,
    val favorite: Boolean,
    val attempts: Int,
    val updatedAt: Long,
)

