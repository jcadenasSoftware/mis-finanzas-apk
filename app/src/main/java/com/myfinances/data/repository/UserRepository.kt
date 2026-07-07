package com.jcadenas.xpendz.data.repository

import com.jcadenas.xpendz.data.local.dao.UserDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val userDao: UserDao
) {
    suspend fun deleteByUid(userUid: String) {
        userDao.delete(userUid)
    }
}
