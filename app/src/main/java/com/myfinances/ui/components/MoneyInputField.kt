package com.jcadenas.xpendz.ui.components

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale

internal enum class MoneyInputFieldVariant {
    BASIC,
    OUTLINED
}

internal object MoneyInputFormatter {
    private val defaultLocale = Locale("es", "CO")

    fun normalizeInput(input: String, locale: Locale = defaultLocale): String {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return ""

        val separators = trimmed.withIndex().filter { it.value == '.' || it.value == ',' }
        if (separators.isEmpty()) {
            return normalizeIntegerDigits(trimmed.filter { it.isDigit() })
        }

        val lastSeparator = separators.last()
        val lastSeparatorChar = lastSeparator.value
        val digitsAfterLastSeparator = trimmed.substring(lastSeparator.index + 1).count { it.isDigit() }
        val localeDecimalSeparator = DecimalFormatSymbols.getInstance(locale).decimalSeparator

        val shouldTreatAsDecimal = when {
            separators.size > 1 -> digitsAfterLastSeparator != 3
            lastSeparatorChar == localeDecimalSeparator -> true
            digitsAfterLastSeparator == 3 -> false
            else -> true
        }

        return if (!shouldTreatAsDecimal) {
            normalizeIntegerDigits(trimmed.filter { it.isDigit() })
        } else {
            val integerDigits = normalizeIntegerDigits(trimmed.substring(0, lastSeparator.index).filter { it.isDigit() })
                .ifBlank { "0" }
            val fractionDigits = trimmed.substring(lastSeparator.index + 1).filter { it.isDigit() }
            if (fractionDigits.isEmpty() && trimmed.endsWith(lastSeparatorChar)) {
                "$integerDigits."
            } else if (fractionDigits.isEmpty()) {
                integerDigits
            } else {
                "$integerDigits.$fractionDigits"
            }
        }
    }

    fun formatForDisplay(input: String, locale: Locale = defaultLocale): String {
        val normalized = normalizeInput(input, locale)
        if (normalized.isBlank()) return ""

        val symbols = DecimalFormatSymbols.getInstance(locale)
        val groupingSeparator = symbols.groupingSeparator
        val decimalSeparator = symbols.decimalSeparator

        val hasDecimalSeparator = normalized.contains('.')
        val parts = normalized.split('.', limit = 2)
        val integerPart = normalizeIntegerDigits(parts.first()).ifBlank { "0" }
        val groupedInteger = groupIntegerDigits(integerPart, groupingSeparator)
        val fractionPart = parts.getOrNull(1).orEmpty()

        return if (hasDecimalSeparator) {
            "${groupedInteger}${decimalSeparator}${fractionPart}"
        } else {
            groupedInteger
        }
    }

    fun parseToCents(input: String, locale: Locale = defaultLocale): Long? {
        val normalized = normalizeInput(input, locale)
        if (normalized.isBlank()) return null

        return try {
            val normalizedNumber = normalized.removeSuffix(".")
            if (normalizedNumber.isBlank()) {
                null
            } else {
                val value = normalizedNumber.toBigDecimal()
                if (value < BigDecimal.ZERO) {
                    null
                } else {
                    value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun formatFromCents(cents: Long, locale: Locale = defaultLocale): String {
        val numberFormat = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 2
        }
        return numberFormat.format(BigDecimal(cents).movePointLeft(2))
    }

    private fun normalizeIntegerDigits(rawDigits: String): String {
        val cleaned = rawDigits.filter { it.isDigit() }.trimStart('0')
        return if (cleaned.isBlank()) {
            if (rawDigits.any { it.isDigit() }) "0" else ""
        } else {
            cleaned
        }
    }

    private fun groupIntegerDigits(rawDigits: String, separator: Char): String {
        val normalizedDigits = normalizeIntegerDigits(rawDigits)
        if (normalizedDigits.isBlank()) return ""

        val firstGroupSize = normalizedDigits.length % 3
        val builder = StringBuilder()
        var index = 0
        val initialGroup = if (firstGroupSize == 0) 3 else firstGroupSize
        builder.append(normalizedDigits.substring(0, initialGroup))
        index = initialGroup

        while (index < normalizedDigits.length) {
            builder.append(separator)
            val end = (index + 3).coerceAtMost(normalizedDigits.length)
            builder.append(normalizedDigits.substring(index, end))
            index = end
        }

        return builder.toString()
    }
}

private class MoneyVisualTransformation(
    private val locale: Locale
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val normalized = MoneyInputFormatter.normalizeInput(text.text, locale)
        val transformed = MoneyInputFormatter.formatForDisplay(normalized, locale)

        val originalToTransformed = IntArray(normalized.length + 1) { offset ->
            MoneyInputFormatter.formatForDisplay(normalized.take(offset), locale).length
        }

        val transformedToOriginal = IntArray(transformed.length + 1)
        var originalIndex = 0
        for (transformedOffset in 0..transformed.length) {
            while (originalIndex + 1 < originalToTransformed.size && originalToTransformed[originalIndex + 1] <= transformedOffset) {
                originalIndex++
            }
            transformedToOriginal[transformedOffset] = originalIndex
        }

        return TransformedText(
            AnnotatedString(transformed),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int {
                    val safeOffset = offset.coerceIn(0, normalized.length)
                    return originalToTransformed[safeOffset]
                }

                override fun transformedToOriginal(offset: Int): Int {
                    val safeOffset = offset.coerceIn(0, transformed.length)
                    return transformedToOriginal[safeOffset]
                }
            }
        )
    }
}

@Composable
internal fun MoneyInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    variant: MoneyInputFieldVariant = MoneyInputFieldVariant.OUTLINED,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    textStyle: TextStyle = TextStyle.Default,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.extraLarge,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    locale: Locale = Locale("es", "CO")
) {
    val visualTransformation = remember(locale) { MoneyVisualTransformation(locale) }
    val normalizedValue = remember(value, locale) { MoneyInputFormatter.normalizeInput(value, locale) }

    when (variant) {
        MoneyInputFieldVariant.BASIC -> {
            BasicTextField(
                value = normalizedValue,
                onValueChange = { onValueChange(MoneyInputFormatter.normalizeInput(it, locale)) },
                modifier = modifier,
                enabled = enabled,
                readOnly = readOnly,
                singleLine = singleLine,
                textStyle = textStyle,
                keyboardOptions = keyboardOptions,
                visualTransformation = visualTransformation,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
            )
        }

        MoneyInputFieldVariant.OUTLINED -> {
            OutlinedTextField(
                value = normalizedValue,
                onValueChange = { onValueChange(MoneyInputFormatter.normalizeInput(it, locale)) },
                modifier = modifier,
                label = label,
                placeholder = placeholder,
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
                supportingText = supportingText,
                isError = isError,
                enabled = enabled,
                readOnly = readOnly,
                singleLine = singleLine,
                textStyle = textStyle,
                shape = shape,
                colors = colors,
                keyboardOptions = keyboardOptions,
                visualTransformation = visualTransformation
            )
        }
    }
}
