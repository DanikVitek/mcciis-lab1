package me.danikvitek.lab1.presentation.fieldState

import androidx.compose.ui.text.input.KeyboardType

interface Constraint<T> {
    fun check(value: T?): Boolean
    val message: String
    val keyboardType: KeyboardType

    object PositiveInt : Constraint<Int> {
        override fun check(value: Int?) = value != null && value > 0
        override val message = "Must be a positive integer"
        override val keyboardType = KeyboardType.Number
    }

    object NonNegativeInt : Constraint<Int> {
        override fun check(value: Int?) = value != null && value >= 0
        override val message = "Must be a non-negative integer"
        override val keyboardType = KeyboardType.Number
    }

    data class RangeInt(private val range: IntRange) : Constraint<Int> {
        override fun check(value: Int?) = value != null && value in range
        override val message by lazy { "Must be an integer in range $range" }
        override val keyboardType = if (range.first < 0) KeyboardType.NumberSigned else KeyboardType.Number
    }

    object PositiveFloat : Constraint<Float> {
        override fun check(value: Float?) = value != null && value > 0
        override val message = "Must be a positive FP number"
        override val keyboardType = KeyboardType.Decimal
    }

    object NonNegativeFloat : Constraint<Float> {
        override fun check(value: Float?) = value != null && value >= 0
        override val message = "Must be a non-negative FP number"
        override val keyboardType = KeyboardType.Decimal
    }

    data class ClosedRangeFloat(private val range: ClosedFloatingPointRange<Float>) : Constraint<Float> {
        override fun check(value: Float?) = value != null && value in range
        override val message by lazy { "Must be an FP number in range $range" }
        override val keyboardType = if (range.start < 0) KeyboardType.DecimalSigned else KeyboardType.Decimal
    }

    data class OpenEndRangeFloat(private val range: OpenEndRange<Float>) : Constraint<Float> {
        override fun check(value: Float?) = value != null && value in range
        override val message by lazy { "Must be an FP number in range $range" }
        override val keyboardType = if (range.start < 0) KeyboardType.DecimalSigned else KeyboardType.Decimal
    }

    companion object {
        fun RangeFloat(range: ClosedFloatingPointRange<Float>) = ClosedRangeFloat(range)
        fun RangeFloat(range: OpenEndRange<Float>) = OpenEndRangeFloat(range)
    }
}