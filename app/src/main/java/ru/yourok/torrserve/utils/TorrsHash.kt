import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater

enum class Tag(val value: Int) {
    TITLE(0), POSTER(1), TRACKER(2), CATEGORY(3), SIZE(4);

    companion object {
        fun fromInt(v: Int) = values().firstOrNull { it.value == v }
    }
}

data class Field(val tag: Tag, val value: String)

class TorrsHash(
    var hash: String = "",
    val fields: MutableList<Field> = mutableListOf()
) {
    fun getTitle() = fields.find { it.tag == Tag.TITLE }?.value ?: ""
    fun getPoster() = fields.find { it.tag == Tag.POSTER }?.value ?: ""
    fun getCategory() = fields.find { it.tag == Tag.CATEGORY }?.value ?: ""
    fun getSize() = fields.find { it.tag == Tag.SIZE }?.value ?: ""
}

object TorrsParser {
    private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

    fun unpack(token: String): TorrsHash {
        val cleanToken = token.replace("torrs://", "").trim()
        val decodedBytes = decode62(cleanToken)
        val decompressed = decompressZlib(decodedBytes)

        val buffer = ByteBuffer.wrap(decompressed).order(ByteOrder.LITTLE_ENDIAN)

        // 1. Читаем Hash (20 байт)
        val hashBytes = ByteArray(20)
        buffer.get(hashBytes)
        val hashHex = hashBytes.joinToString("") { "%02x".format(it) }

        val result = TorrsHash(hashHex)

        // 2. Читаем поля до конца
        while (buffer.hasRemaining()) {
            val tagInt = buffer.get().toInt()
            val tag = Tag.fromInt(tagInt) ?: continue

            if (tag == Tag.SIZE) {
                // Бинарное чтение (int64 / Long)
                val sizeVal = buffer.long
                result.fields.add(Field(tag, sizeVal.toString()))
            } else {
                // Чтение строки (uint16 длина + байты)
                val length = buffer.short.toInt() and 0xFFFF
                val strBytes = ByteArray(length)
                buffer.get(strBytes)
                result.fields.add(Field(tag, String(strBytes, Charsets.UTF_8)))
            }
        }
        return result
    }

    private fun decode62(input: String): ByteArray {
        var res = BigInteger.ZERO
        val base = BigInteger.valueOf(62)
        for (char in input) {
            val digit = ALPHABET.indexOf(char)
            res = res.multiply(base).add(BigInteger.valueOf(digit.toLong()))
        }
        return res.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
    }

    private fun decompressZlib(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val outputStream = ByteArrayOutputStream(data.size)
        val buffer = ByteArray(1024)
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            outputStream.write(buffer, 0, count)
        }
        outputStream.close()
        return outputStream.toByteArray()
    }
}