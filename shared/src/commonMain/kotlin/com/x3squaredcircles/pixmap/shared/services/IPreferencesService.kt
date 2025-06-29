package com.x3squaredcircles.pixmap.shared.services

/**
 * Interface for preferences/settings storage
 */
interface IPreferencesService {
    suspend fun getString(key: String, defaultValue: String = ""): String
    suspend fun setString(key: String, value: String)
    suspend fun getInt(key: String, defaultValue: Int = 0): Int
    suspend fun setInt(key: String, value: Int)
    suspend fun getBoolean(key: String, defaultValue: Boolean = false): Boolean
    suspend fun setBoolean(key: String, value: Boolean)
    suspend fun getLong(key: String, defaultValue: Long = 0L): Long
    suspend fun setLong(key: String, value: Long)
    suspend fun getFloat(key: String, defaultValue: Float = 0f): Float
    suspend fun setFloat(key: String, value: Float)
    suspend fun remove(key: String)
    suspend fun clear()
    suspend fun contains(key: String): Boolean
}