package ai.assistant.llm

import android.content.Context
import android.util.Log
import androidx.constraintlayout.helper.widget.MotionEffect
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL


object ModelDownloader {
    fun downloadModel(
        context: Context,
        url: String?,
        fileName: String,
        callback: DownloadCallback?
    ) {
        try {
            val file = File(context.filesDir, fileName)
            val tempFile = File(context.filesDir, "$fileName.tmp")
            val modelUrl = URL(url)
            val connection = modelUrl.openConnection() as HttpURLConnection
            connection.connect()

            // Check if response code is OK
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val outputStream = FileOutputStream(tempFile)

                val buffer = ByteArray(4096)
                var bytesRead: Int
                while ((inputStream.read(buffer).also { bytesRead = it }) != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                // File downloaded successfully
                if (tempFile.renameTo(file)) {
                    Log.d(MotionEffect.TAG, "File downloaded successfully")
                    callback?.onDownloadComplete()
                } else {
                    Log.e(MotionEffect.TAG, "Failed to rename temp file to original file")
                }
            } else {
                Log.e(
                    MotionEffect.TAG,
                    "Failed to download model. HTTP response code: " + connection.responseCode
                )
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e(MotionEffect.TAG, "Exception occurred during model download: " + e.message)
        } catch (e: GenAIException) {
            throw RuntimeException(e)
        }
    }

    interface DownloadCallback {
        @Throws(GenAIException::class)
        fun onDownloadComplete()
    }
}