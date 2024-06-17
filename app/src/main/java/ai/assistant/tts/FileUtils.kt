package ai.assistant.tts

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

object FileUtils {
    @Throws(IOException::class)
    fun read(file: File): String {
        return FileUtils.readByteArray(
            FileInputStream(file),
            file.length().toInt()
        ).toString()
    }

    @Throws(IOException::class)
    fun read(stream: InputStream): String {
        return FileUtils.readByteArray(stream, stream.available()).toString()
    }

    @Throws(IOException::class)
    fun read(stream: InputStream?, length: Int): String {
        return FileUtils.readByteArray(stream!!, length).toString()
    }

    @Throws(IOException::class)
    fun readBinary(file: File): ByteArray {
        return FileUtils.readByteArray(
            FileInputStream(file),
            file.length().toInt()
        ).toByteArray()
    }

    @Throws(IOException::class)
    fun readBinary(stream: InputStream): ByteArray {
        return FileUtils.readByteArray(stream, stream.available())
            .toByteArray()
    }

    @Throws(IOException::class)
    fun readBinary(stream: InputStream?, length: Int): ByteArray {
        return FileUtils.readByteArray(stream!!, length).toByteArray()
    }

    @Throws(IOException::class)
    private fun readByteArray(stream: InputStream, length: Int): ByteArrayOutputStream {
        val content = ByteArrayOutputStream(length)
        var c = stream.read()
        while (c != -1) {
            content.write(c.toByte().toInt())
            c = stream.read()
        }
        return content
    }

    @Throws(IOException::class)
    fun write(outputFile: File?, contents: String) {
        FileUtils.write(outputFile, contents.toByteArray())
    }

    @Throws(IOException::class)
    fun write(outputFile: File?, contents: ByteArray) {
        val outputStream = FileOutputStream(outputFile)
        try {
            outputStream.write(contents, 0, contents.size)
        } finally {
            outputStream.close()
        }
    }

    fun rmdir(directory: File) {
        if (!directory.exists() || !directory.isDirectory) {
            return
        }

        for (child in directory.listFiles()) {
            if (child.isDirectory) {
                FileUtils.rmdir(child)
            }

            child.delete()
        }
    }
}