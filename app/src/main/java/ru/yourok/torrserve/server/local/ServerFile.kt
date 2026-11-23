package ru.yourok.torrserve.server.local

import android.content.Context
import android.os.Build
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
        // Проверяем архитектуру до удаления рабочего бинарника: чужой файл не должен
        // оставить приложение без сервера
        checkCompatible(source)
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

    /**
     * Файл должен быть исполняемым ELF, собранным под ABI этого устройства.
     * Иначе сервер не запустится (shell пишет `not executable: 64-bit ELF file`),
     * а пользователь видит только «не удалось загрузить список».
     */
    private fun checkCompatible(file: File) {
        val head = ByteArray(20)
        val read = file.inputStream().use { it.read(head) }
        val elf = read == head.size &&
            head[0] == 0x7F.toByte() &&
            head[1] == 'E'.code.toByte() &&
            head[2] == 'L'.code.toByte() &&
            head[3] == 'F'.code.toByte()
        if (!elf) throw IllegalStateException("файл не является исполняемым файлом Linux (ELF)")
        val is64 = head[4].toInt() == 2
        val machine = (head[18].toInt() and 0xFF) or ((head[19].toInt() and 0xFF) shl 8)
        if (!supported(is64, machine)) {
            val device = Build.SUPPORTED_ABIS.firstOrNull() ?: "armeabi-v7a"
            throw IllegalStateException(
                "бинарник для ${elfArch(is64, machine)}, а устройство - $device"
            )
        }
    }

    /** true, если такой ELF может работать на устройстве (ABI из Build.SUPPORTED_ABIS). */
    private fun supported(is64: Boolean, machine: Int): Boolean = Build.SUPPORTED_ABIS.any { abi ->
        when (abi) {
            "arm64-v8a" -> is64 && machine == MACHINE_ARM64
            "armeabi-v7a", "armeabi" -> !is64 && machine == MACHINE_ARM
            "x86_64" -> is64 && machine == MACHINE_X86_64
            "x86" -> !is64 && machine == MACHINE_X86
            else -> false
        }
    }

    /** Имя архитектуры как у файлов релиза: arm64, arm7, amd64, 386. */
    private fun elfArch(is64: Boolean, machine: Int): String = when (machine) {
        MACHINE_ARM64 -> "arm64"
        MACHINE_ARM -> if (is64) "arm64" else "arm7"
        MACHINE_X86_64 -> "amd64"
        MACHINE_X86 -> "386"
        else -> "неизвестная архитектура"
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
        /** e_machine из ELF-заголовка. */
        private const val MACHINE_ARM = 40
        private const val MACHINE_X86_64 = 62
        private const val MACHINE_X86 = 3
        private const val MACHINE_ARM64 = 183

        const val BIN_NAME = "silo"

        /** Конфиг сервера рядом с бинарником (пишется при каждом старте). */
        const val CONFIG_NAME = "config.yaml"

        /** Вывод процесса сервера (stdout/stderr). */
        const val OUT_NAME = "silo.out"

        /** Файл лога сервера (logging.file в конфиге). */
        const val LOG_NAME = "silo.log"
    }
}
