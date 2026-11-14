package ru.yourok.torrserve.server.local

import android.content.Context
import com.topjohnwu.superuser.Shell
import java.io.File

/**
 * Бинарник сервера Silo, скачанный в filesDir.
 * Запуск и остановка - через libsu (как в старом клиенте).
 */
class ServerFile(context: Context) : File(context.filesDir, BIN_NAME) {

    private val lock = Any()
    private var shellJob: Shell.Job? = null

    /**
     * Запускает сервер. Silo принимает только `-c <config.yaml>`,
     * поэтому конфиг пишется рядом с бинарником (в filesDir).
     */
    fun run(config: File) {
        if (!exists()) return
        synchronized(lock) {
            // Всегда перезапускаем: новый конфиг (например, с паролем owner) применяется только при старте
            stopProcess()
            val shell = Shell.Builder.create()
                .setFlags(Shell.FLAG_NON_ROOT_SHELL)
                .build()
            shellJob = shell.newJob()
                .add("export GODEBUG=madvdontneed=1")
                .add("cd ${parentFile!!.path} && $path -c ${config.path} >${config.parentFile!!.path}/$OUT_NAME 2>&1 &")
            shellJob?.exec()
        }
    }

    /** Останавливает сервер. */
    fun stop() {
        if (!exists()) return
        synchronized(lock) {
            stopProcess()
        }
    }

    private fun stopProcess() {
        Shell.sh("killall -9 $BIN_NAME").exec()
        shellJob = null
    }

    /**
     * Ставит сервер из выбранного файла (например, скачанного браузером в Download).
     * Старый бинарник удаляется, новый получает право на исполнение.
     * Бросает исключение, если скопировать или сделать файл запускаемым не удалось.
     */
    fun installFrom(source: File) {
        stop()
        synchronized(lock) {
            if (exists() && !delete()) throw IllegalStateException("cannot delete old server")
            try {
                source.inputStream().use { input ->
                    outputStream().use { out -> input.copyTo(out) }
                }
                if (!setExecutable(true, true)) throw IllegalStateException("cannot set exec permission")
            } catch (e: Exception) {
                delete()
                throw e
            }
        }
    }

    /** Останавливает сервер и удаляет его файлы: бинарник, конфиг, лог и вывод процесса. */
    fun uninstall() {
        stop()
        val dir = parentFile ?: return
        delete()
        File(dir, CONFIG_NAME).delete()
        File(dir, LOG_NAME).delete()
        File(dir, OUT_NAME).delete()
    }

    companion object {
        const val BIN_NAME = "silo"

        /** Конфиг сервера рядом с бинарником (пишется при каждом старте). */
        const val CONFIG_NAME = "config.yaml"

        /** Вывод процесса сервера (stdout/stderr). */
        const val OUT_NAME = "silo.out"

        /** Файл лога сервера (logging.file в конфиге). */
        const val LOG_NAME = "silo.log"
    }
}
