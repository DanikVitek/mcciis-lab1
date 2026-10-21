package me.danikvitek.lab1.presentation.fieldState

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.KeyboardType
import lab1.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

interface Constraint<T> {
    fun check(value: T?): Boolean
    val message: String
    val keyboardType: KeyboardType

    @Composable
    fun localizedMessage(): String = message

    object PositiveInt : Constraint<Int> {
        override fun check(value: Int?) = value != null && value > 0
        override val message = "Must be a positive integer"
        override val keyboardType = KeyboardType.Number

        @Composable
        override fun localizedMessage() = stringResource(Res.string.err_positive_int)
    }

    object NonNegativeInt : Constraint<Int> {
        override fun check(value: Int?) = value != null && value >= 0
        override val message = "Must be a non-negative integer"
        override val keyboardType = KeyboardType.Number

        @Composable
        override fun localizedMessage() = stringResource(Res.string.err_non_negative_int)
    }

    data class RangeInt(private val range: IntRange) : Constraint<Int> {
        override fun check(value: Int?) = value != null && value in range
        override val message by lazy { "Must be an integer in range $range" }
        override val keyboardType = if (range.first < 0) KeyboardType.NumberSigned else KeyboardType.Number

        @Composable
        override fun localizedMessage() = stringResource(Res.string.err_range_int, range.toString())
    }

    object PositiveFloat : Constraint<Float> {
        override fun check(value: Float?) = value != null && value > 0
        override val message = "Must be a positive FP number"
        override val keyboardType = KeyboardType.Decimal

        @Composable
        override fun localizedMessage() = stringResource(Res.string.err_positive_float)
    }

    object NonNegativeFloat : Constraint<Float> {
        override fun check(value: Float?) = value != null && value >= 0
        override val message = "Must be a non-negative FP number"
        override val keyboardType = KeyboardType.Decimal

        @Composable
        override fun localizedMessage() = stringResource(Res.string.err_non_negative_float)
    }

    data class ClosedRangeFloat(private val range: ClosedFloatingPointRange<Float>) : Constraint<Float> {
        override fun check(value: Float?) = value != null && value in range
        override val message by lazy { "Must be an FP number in range $range" }
        override val keyboardType = if (range.start < 0) KeyboardType.DecimalSigned else KeyboardType.Decimal

        @Composable
        override fun localizedMessage() = stringResource(Res.string.err_range_float, range.toString())
    }

    data class OpenEndRangeFloat(private val range: OpenEndRange<Float>) : Constraint<Float> {
        override fun check(value: Float?) = value != null && value in range
        override val message by lazy { "Must be an FP number in range $range" }
        override val keyboardType = if (range.start < 0) KeyboardType.DecimalSigned else KeyboardType.Decimal

        @Composable
        override fun localizedMessage() = stringResource(Res.string.err_range_float, range.toString())
    }

    companion object {
        fun RangeFloat(range: ClosedFloatingPointRange<Float>) = ClosedRangeFloat(range)
        fun RangeFloat(range: OpenEndRange<Float>) = OpenEndRangeFloat(range)
    }
}
