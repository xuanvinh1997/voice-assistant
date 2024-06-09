package ai.assistant

import android.content.Context
import android.content.res.AssetManager
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream


class Utils {
    companion object {
        fun copyAssetsToInternalStorage(context: Context, asset: Int, fileName: String): String {
            val inputStream: InputStream = context.resources.openRawResource(asset)
            val file = File(context.filesDir, fileName)
            val outputStream = FileOutputStream(file)
            copyFile(inputStream, outputStream)
            inputStream.close()
            outputStream.close()
            return file.absolutePath
        }

        @Throws(IOException::class)
        fun copyFile(`in`: InputStream, out: OutputStream) {
            val buffer = ByteArray(1024)
            var read: Int
            while ((`in`.read(buffer).also { read = it }) != -1) {
                out.write(buffer, 0, read)
            }
        }

    }
}