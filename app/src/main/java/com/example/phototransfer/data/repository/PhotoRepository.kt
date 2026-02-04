package com.example.phototransfer.data.repository

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhotoRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val TAG = "PhotoRepository"
    }

    suspend fun savePhoto(bitmap: Bitmap, fileName: String): Uri? = withContext(Dispatchers.IO) {
        val contentResolver: ContentResolver = context.contentResolver
        val imageUri: Uri?
        val outputStream: OutputStream?
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PhotoTransfer")
            }
            
            imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            outputStream = imageUri?.let { contentResolver.openOutputStream(it) }
        } else {
            val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val photoTransferDir = File(imagesDir, "PhotoTransfer")
            if (!photoTransferDir.exists()) {
                photoTransferDir.mkdirs()
            }
            
            val imageFile = File(photoTransferDir, fileName)
            imageUri = Uri.fromFile(imageFile)
            outputStream = FileOutputStream(imageFile)
        }
        
        outputStream?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        
        imageUri
    }
    
    suspend fun getPhotosFromGallery(): List<Uri> = withContext(Dispatchers.IO) {
        val photos = mutableListOf<Uri>()
        val contentResolver = context.contentResolver
        
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED
        )
        
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val contentUri = Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )
                photos.add(contentUri)
            }
        }
        
        photos
    }

    /**
     * 将接收的文件保存到系统相册（MediaStore）
     * @param sourceFile 源文件（临时文件）
     * @param fileName 文件名
     * @return 保存后的 Uri，失败返回 null
     */
    suspend fun saveReceivedFileToGallery(sourceFile: File, fileName: String): Uri? = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== Starting saveReceivedFileToGallery ===")
        Log.d(TAG, "Source file: ${sourceFile.absolutePath}")
        Log.d(TAG, "File name: $fileName")
        Log.d(TAG, "File exists: ${sourceFile.exists()}")
        Log.d(TAG, "File size: ${sourceFile.length()} bytes")
        Log.d(TAG, "File can read: ${sourceFile.canRead()}")
        Log.d(TAG, "File is file: ${sourceFile.isFile}")
        Log.d(TAG, "Android version: ${Build.VERSION.SDK_INT}")

        // Validate source file
        if (!sourceFile.exists()) {
            Log.e(TAG, "❌ Source file does not exist")
            return@withContext null
        }

        if (!sourceFile.canRead()) {
            Log.e(TAG, "❌ Source file is not readable")
            return@withContext null
        }

        if (!sourceFile.isFile) {
            Log.e(TAG, "❌ Source is not a file")
            return@withContext null
        }

        if (sourceFile.length() == 0L) {
            Log.e(TAG, "❌ Source file is empty")
            return@withContext null
        }

        try {
            val contentResolver: ContentResolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val relativePath = Environment.DIRECTORY_PICTURES + "/PhotoTransfer"
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                    Log.d(TAG, "Using Scoped Storage, relative path: $relativePath")
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            Log.d(TAG, "Collection URI: $collection")

            val imageUri = contentResolver.insert(collection, contentValues)
            Log.d(TAG, "Insert result URI: $imageUri")

            if (imageUri == null) {
                Log.e(TAG, "Failed to insert into MediaStore, URI is null")
                return@withContext null
            }

            imageUri.let { uri ->
                Log.d(TAG, "Opening output stream for URI: $uri")
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    Log.d(TAG, "Copying file content...")
                    val sourceFileSize = sourceFile.length()
                    sourceFile.inputStream().use { inputStream ->
                        val bytesCopied = inputStream.copyTo(outputStream)
                        Log.d(TAG, "Copied $bytesCopied bytes (expected: $sourceFileSize bytes)")

                        // Verify complete copy
                        if (bytesCopied != sourceFileSize) {
                            Log.e(TAG, "❌ Incomplete copy: $bytesCopied != $sourceFileSize")
                            return@withContext null
                        }
                    }
                    Log.d(TAG, "File content copied successfully")
                } ?: run {
                    Log.e(TAG, "Failed to open output stream")
                    return@withContext null
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Log.d(TAG, "Clearing IS_PENDING flag...")
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    val updateCount = contentResolver.update(uri, contentValues, null, null)
                    Log.d(TAG, "Update count: $updateCount")
                }

                Log.d(TAG, "✅ Successfully saved to gallery: $uri")
                uri
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Security exception - permission denied", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving received file to gallery", e)
            e.printStackTrace()
            null
        }
    }

    /**
     * 保存文件到应用私有目录（作为 MediaStore 保存失败时的回退方案）
     * @param sourceFile 源文件（临时文件）
     * @param fileName 文件名
     * @return 保存后的 Uri，失败返回 null
     */
    suspend fun saveToPrivateDirectory(sourceFile: File, fileName: String): Uri? = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== Saving to private directory (fallback) ===")
        Log.d(TAG, "Source file: ${sourceFile.absolutePath}")
        Log.d(TAG, "File name: $fileName")

        try {
            // 使用应用私有外部存储目录
            val privateDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            if (privateDir == null) {
                Log.e(TAG, "❌ Failed to get private external files directory")
                return@withContext null
            }

            // 创建 PhotoTransfer 子目录
            val photoTransferDir = File(privateDir, "PhotoTransfer")
            if (!photoTransferDir.exists()) {
                val created = photoTransferDir.mkdirs()
                Log.d(TAG, "Created private directory: $created")
            }

            val destinationFile = File(photoTransferDir, fileName)
            Log.d(TAG, "Destination file: ${destinationFile.absolutePath}")

            // 复制文件
            sourceFile.inputStream().use { input ->
                destinationFile.outputStream().use { output ->
                    val bytesCopied = input.copyTo(output)
                    Log.d(TAG, "Copied $bytesCopied bytes to private directory")
                }
            }

            // 验证文件
            if (destinationFile.exists() && destinationFile.length() == sourceFile.length()) {
                val uri = Uri.fromFile(destinationFile)
                Log.d(TAG, "✅ Successfully saved to private directory: $uri")
                uri
            } else {
                Log.e(TAG, "❌ File verification failed after copy")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving to private directory", e)
            e.printStackTrace()
            null
        }
    }

    /**
     * 通过 URI 使用 ContentResolver 保存接收的文件到系统相册
     * 这是 Android 10+ Scoped Storage 的正确处理方式
     * Nearby Connections 将文件保存到 Download/Nearby 目录，应用无法直接用 File API 读取
     * 必须通过 ContentResolver 使用 URI 来读取文件内容
     */
    suspend fun saveReceivedFileFromUri(sourceUri: Uri, fileName: String): Uri? = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== Starting saveReceivedFileFromUri ===")
        Log.d(TAG, "Source URI: $sourceUri")
        Log.d(TAG, "File name: $fileName")
        Log.d(TAG, "Android version: ${Build.VERSION.SDK_INT}")

        try {
            val contentResolver: ContentResolver = context.contentResolver

            val inputStream = try {
                contentResolver.openInputStream(sourceUri)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to open source URI input stream", e)
                null
            }

            if (inputStream == null) {
                Log.e(TAG, "❌ Source URI input stream is null")
                return@withContext null
            }

            val finalFileName = if (!fileName.contains(".")) "${fileName}.jpg" else fileName
            Log.d(TAG, "Final file name: $finalFileName")

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, finalFileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PhotoTransfer")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                    Log.d(TAG, "Using Scoped Storage, relative path: Pictures/PhotoTransfer")
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            Log.d(TAG, "Collection URI: $collection")

            val imageUri = contentResolver.insert(collection, contentValues)
            Log.d(TAG, "Insert result URI: $imageUri")

            if (imageUri == null) {
                Log.e(TAG, "Failed to insert into MediaStore, URI is null")
                inputStream.close()
                return@withContext null
            }

            contentResolver.openOutputStream(imageUri)?.use { outputStream ->
                Log.d(TAG, "Copying file content from URI...")
                inputStream.use { input ->
                    val bytesCopied = input.copyTo(outputStream)
                    Log.d(TAG, "Copied $bytesCopied bytes")
                }
                Log.d(TAG, "File content copied successfully")
            } ?: run {
                Log.e(TAG, "Failed to open output stream")
                inputStream.close()
                return@withContext null
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Log.d(TAG, "Clearing IS_PENDING flag...")
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                val updateCount = contentResolver.update(imageUri, contentValues, null, null)
                Log.d(TAG, "Update count: $updateCount")
            }

            Log.d(TAG, "✅ Successfully saved to gallery from URI: $imageUri")
            imageUri
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Security exception - permission denied", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving received file from URI to gallery", e)
            e.printStackTrace()
            null
        }
    }

    /**
     * 通过 URI 保存文件到应用私有目录（回退方案）
     */
    suspend fun saveToPrivateDirectoryFromUri(sourceUri: Uri, fileName: String): Uri? = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== Saving to private directory from URI (fallback) ===")
        Log.d(TAG, "Source URI: $sourceUri")
        Log.d(TAG, "File name: $fileName")

        try {
            val contentResolver: ContentResolver = context.contentResolver

            val inputStream = try {
                contentResolver.openInputStream(sourceUri)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to open source URI input stream", e)
                null
            }

            if (inputStream == null) {
                Log.e(TAG, "❌ Source URI input stream is null")
                return@withContext null
            }

            val privateDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            if (privateDir == null) {
                Log.e(TAG, "❌ Failed to get private external files directory")
                inputStream.close()
                return@withContext null
            }

            val photoTransferDir = File(privateDir, "PhotoTransfer")
            if (!photoTransferDir.exists()) {
                val created = photoTransferDir.mkdirs()
                Log.d(TAG, "Created private directory: $created")
            }

            val finalFileName = if (!fileName.contains(".")) "${fileName}.jpg" else fileName
            val destinationFile = File(photoTransferDir, finalFileName)
            Log.d(TAG, "Destination file: ${destinationFile.absolutePath}")

            inputStream.use { input ->
                destinationFile.outputStream().use { output ->
                    val bytesCopied = input.copyTo(output)
                    Log.d(TAG, "Copied $bytesCopied bytes to private directory")
                }
            }

            if (destinationFile.exists() && destinationFile.length() > 0) {
                val uri = Uri.fromFile(destinationFile)
                Log.d(TAG, "✅ Successfully saved to private directory from URI: $uri")
                uri
            } else {
                Log.e(TAG, "❌ File verification failed after copy")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving to private directory from URI", e)
            e.printStackTrace()
            null
        }
    }
}
