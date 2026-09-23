package com.xiaoyunduo.zhiti.data

import android.content.Context
import com.xiaoyunduo.zhiti.data.api.ZhitiApi
import com.xiaoyunduo.zhiti.data.content.ContentRepository
import com.xiaoyunduo.zhiti.data.user.UserDatabase

class AppContainer(context: Context, database: UserDatabase) {
    val preferences = AppPreferences(context)
    val api = ZhitiApi()
    val content = ContentRepository(context)
    val answers = database.answerDao()
}

