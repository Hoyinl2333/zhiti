package com.xiaoyunduo.zhiti.data.user

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [AnswerRecord::class], version = 1, exportSchema = false)
abstract class UserDatabase : RoomDatabase() {
    abstract fun answerDao(): AnswerDao
}
