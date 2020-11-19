package ru.yourok.torrserve.server.local

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

object Updater {
    fun updateFromFile(filePath: String) {
        val file = File(filePath)
        if (file.canRead()) {
            val serverFile = ServerFile()
            serverFile.delete()
            val input = FileInputStream(file)
            val output = FileOutputStream(serverFile)
            input.copyTo(output)
            input.close()
            output.flush()
            output.close()
            if (!serverFile.setExecutable(true))
                throw IOException("error set server exec permission")
        }
    }
}