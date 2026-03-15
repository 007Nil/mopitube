package com.nil.mopitube.mopidy

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Uploads an image from the device gallery to the server's artwork endpoint.
 *
 * Server contract (POST http://{host}:9000/artwork):
 *   Multipart form-data fields:
 *     - track_uri  (text)  — Mopidy track URI (server derives album directory from file path)
 *     - image      (file)  — raw image bytes
 *   Response: {"status": "ok"} on success
 */
suspend fun uploadArtworkToServer(
    host: String,
    trackUri: String,
    imageUri: Uri,
    context: Context
): Boolean = withContext(Dispatchers.IO) {
    Log.d("ArtworkUploader", "Uploading to http://$host:9000/artwork — trackUri=$trackUri imageUri=$imageUri")
    try {
        val imageBytes = context.contentResolver.openInputStream(imageUri)?.readBytes()
            ?: return@withContext false.also { Log.e("ArtworkUploader", "Failed to open input stream for $imageUri") }
        val mimeType = context.contentResolver.getType(imageUri) ?: "image/jpeg"
        val boundary = "----MopitubeBoundary${System.currentTimeMillis()}"

        val url = URL("http://$host:9000/artwork")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 10000
        conn.readTimeout = 30000
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

        DataOutputStream(conn.outputStream).use { out ->
            // track_uri field
            out.writeBytes("--$boundary\r\n")
            out.writeBytes("Content-Disposition: form-data; name=\"track_uri\"\r\n\r\n")
            out.writeBytes("$trackUri\r\n")
            // image field
            out.writeBytes("--$boundary\r\n")
            out.writeBytes("Content-Disposition: form-data; name=\"image\"; filename=\"cover.jpg\"\r\n")
            out.writeBytes("Content-Type: $mimeType\r\n\r\n")
            out.write(imageBytes)
            out.writeBytes("\r\n--$boundary--\r\n")
        }

        val code = conn.responseCode
        val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.readText() ?: ""
        conn.disconnect()
        Log.d("ArtworkUploader", "Response $code: $body")
        code in 200..299
    } catch (e: Exception) {
        Log.e("ArtworkUploader", "Upload failed", e)
        false
    }
}
