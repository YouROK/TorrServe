package ru.yourok.torrserve.ext

fun String.removeNonPrintable(): String // All Control Char
{
    return this.replace("[\\p{C}]".toRegex(), "")
}

fun String.removeSomeControlChar(): String // Some Control Char
{
    return this.replace("[\\p{Cntrl}\\p{Cc}\\p{Cf}\\p{Co}\\p{Cn}]".toRegex(), "")
}

fun String.removeFullControlChar(): String {
    return removeNonPrintable().replace("[\\r\\n\\t]".toRegex(), "")
}

fun String.removeOtherSymbolChar(): String // Some Control Char
{
    return this.replace("[\\p{So}]".toRegex(), "")
}

fun String.clearName(): String {
    return this.removeOtherSymbolChar()
        .replace(",", " ")
        .replace("\\s+".toRegex(), " ")
        .trim()
}