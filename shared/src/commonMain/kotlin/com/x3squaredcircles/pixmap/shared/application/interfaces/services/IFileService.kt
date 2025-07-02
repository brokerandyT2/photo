package com.x3squaredcircles.pixmap.shared.application.interfaces.services

import com.x3squaredcircles.pixmap.shared.common.Result

/**
 * Interface for file operations
 */
interface IFileService {
    suspend fun saveFile(data: ByteArray, fileName: String): Result<String>
    suspend fun readFile(filePath: String): Result<ByteArray>
    suspend fun deleteFile(filePath: String): Result<Unit>
    suspend fun fileExists(filePath: String): Boolean
    suspend fun createDirectory(path: String): Result<Unit>
    fun getAppDirectory(): String
    fun getPhotosDirectory(): String
}