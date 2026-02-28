package com.manalejandro.alejabber.media

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.manalejandro.alejabber.data.remote.XmppConnectionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jivesoftware.smackx.httpfileupload.HttpFileUploadManager
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HttpUploadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val xmppManager: XmppConnectionManager,
    private val okHttpClient: OkHttpClient
) {
    /**
     * Uploads a file using XEP-0363 http_upload and returns the download URL or null on failure.
     */
    suspend fun uploadFile(accountId: Long, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val connection = xmppManager.getConnection(accountId) ?: return@withContext null
            val uploadManager = HttpFileUploadManager.getInstanceFor(connection)
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "bin"
            val fileName = "upload_${System.currentTimeMillis()}.$extension"
            val bytes = contentResolver.openInputStream(uri)?.readBytes() ?: return@withContext null
            // Write to temp file and upload
            val tempFile = File(context.cacheDir, fileName).also { it.writeBytes(bytes) }
            return@withContext uploadFileInternal(uploadManager, tempFile, mimeType, okHttpClient)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun uploadFile(accountId: Long, file: File, mimeType: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val connection = xmppManager.getConnection(accountId) ?: return@withContext null
                val uploadManager = HttpFileUploadManager.getInstanceFor(connection)
                return@withContext uploadFileInternal(uploadManager, file, mimeType, okHttpClient)
            } catch (e: Exception) {
                null
            }
        }

    private fun uploadFileInternal(
        uploadManager: HttpFileUploadManager,
        file: File,
        mimeType: String,
        okClient: OkHttpClient
    ): String? {
        return try {
            // Request an upload slot (XEP-0363)
            val slot = uploadManager.requestSlot(file.name, file.length(), mimeType)
            val putUrl = slot.putUrl.toString()
            val getUrl = slot.getUrl.toString()
            // PUT the file bytes
            val response = okClient.newCall(
                Request.Builder()
                    .url(putUrl)
                    .put(file.readBytes().toRequestBody(mimeType.toMediaType()))
                    .build()
            ).execute()
            if (response.isSuccessful || response.code == 201) getUrl else null
        } catch (e: Exception) {
            null
        }
    }
}





