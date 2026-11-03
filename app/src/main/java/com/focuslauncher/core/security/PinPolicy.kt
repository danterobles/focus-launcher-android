package com.focuslauncher.core.security

/** Política de PIN: entre 6 y 12 dígitos ASCII `0`–`9`, sin normalización Unicode. */
object PinPolicy {
    const val MIN_LENGTH = 6
    const val MAX_LENGTH = 12

    fun isValid(pin: CharArray): Boolean =
        pin.size in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }

    fun matches(pin: CharArray, confirmation: CharArray): Boolean = pin.contentEquals(confirmation)

    fun wipe(vararg secrets: CharArray) = secrets.forEach { it.fill('\u0000') }
}
