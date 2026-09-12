// 契约：docs/03-导入格式-v1.md §4 测量值限制；docs/05 §8 血糖小数输入
package com.family.health.feature.entry

/** 保留至多 3 位整数和 2 位小数，只接受一个小数点。 */
internal fun sanitizeDecimalInput(raw: String): String {
    val normalized = buildString {
        var hasDot = false
        raw.forEach { char ->
            when {
                char.isDigit() -> append(char)
                char == '.' && !hasDot -> {
                    append(char)
                    hasDot = true
                }
            }
        }
    }
    val parts = normalized.split('.', limit = 2)
    val integer = parts[0].take(3)
    return if (parts.size == 1) integer else "$integer.${parts[1].take(2)}"
}

internal fun validGlucose(value: Double): Boolean = value in 0.5..40.0

internal fun validBloodPressure(value: Int): Boolean = value in 40..300

internal fun validHeartRate(value: Int): Boolean = value in 20..250
