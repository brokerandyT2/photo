package com.x3squaredcircles.pixmap.android.services

import android.content.Context
import com.x3squaredcircles.pixmap.shared.common.Constants
import com.x3squaredcircles.pixmap.shared.common.Result
import com.x3squaredcircles.pixmap.shared.application.interfaces.services.IFileService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Android implementation of file service
 */
class AndroidFileService(
    private val context: Context
) : IFileService {

    override suspend fun saveFile(data: ByteArray, fileName: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(getAppDirectory(), fileName)
                file.parentFile?.let { parentDir ->
                    if (!parentDir.exists()) {
                        parentDir.mkdirs()
                    }
                }

                FileOutputStream(file).use { outputStream ->
                    outputStream.write(data)
                    outputStream.flush()
                }

                Result.success(file.absolutePath)
            } catch (e: Exception) {
                Result.error(e)
            }
        }
    }

    override suspend fun readFile(filePath: String): Result<ByteArray> {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    return@withContext Result.error("File does not exist: $filePath")
                }

                val data = FileInputStream(file).use { inputStream ->
                    inputStream.readBytes()
                }

                Result.success(data)
            } catch (e: Exception) {
                Result.error(e)
            }
        }
    }

    override suspend fun deleteFile(filePath: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (file.exists()) {
                    val deleted = file.delete()
                    if (deleted) {
                        Result.success(Unit)
                    } else {
                        Result.error("Failed to delete file: $filePath")
                    }
                } else {
                    Result.success(Unit)
                }
            } catch (e: Exception) {
                Result.error(e)
            }
        }
    }

    override suspend fun fileExists(filePath: String): Boolean {
        return withContext(Dispatchers.IO) {
            File(filePath).exists()
        }
    }

    override suspend fun createDirectory(path: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val directory = File(path)
                val created = directory.mkdirs()
                if (created || directory.exists()) {
                    Result.success(Unit)
                } else {
                    Result.error("Failed to create directory: $path")
                }
            } catch (e: Exception) {
                Result.error(e)
            }
        }
    }

    override fun getAppDirectory(): String {
        return context.filesDir.absolutePath
    }

    override fun getPhotosDirectory(): String {
        val photosDir = File(getAppDirectory(), Constants.PHOTOS_DIRECTORY)
        if (!photosDir.exists()) {
            photosDir.mkdirs()
        }
        return photosDir.absolutePath
    }
}