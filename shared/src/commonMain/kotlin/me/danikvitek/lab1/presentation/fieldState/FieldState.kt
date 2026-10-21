package me.danikvitek.lab1.presentation.fieldState

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

abstract class FieldState<T : Any>(
    initialValue: String,
    internal val constraint: Constraint<T>,
    conversion: (String) -> T?,
) {
    var text by mutableStateOf(initialValue)
        internal set

    val value: T? by derivedStateOf { conversion(text).takeIf(constraint::check) }
}

class FloatFieldState(initialValue: String, constraint: Constraint<Float>) : FieldState<Float>(
    initialValue, constraint,
    String::toFloatOrNull,
)

class IntFieldState(initialValue: String, constraint: Constraint<Int>) : FieldState<Int>(
    initialValue, constraint,
    String::toIntOrNull,
)

@Composable
fun rememberIntFieldState(initialValue: String, constraint: Constraint<Int>) =
    remember { IntFieldState(initialValue, constraint) }

@Composable
fun rememberFloatFieldState(initialValue: String, constraint: Constraint<Float>) =
    remember { FloatFieldState(initialValue, constraint) }

@Composable
fun <T : Any> ValidatedField(
    modifier: Modifier,
    state: FieldState<T>,
    label: String,
) {
    TextField(
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = state.constraint.keyboardType),
        value = state.text,
        onValueChange = { state.text = it },
        isError = state.value == null,
        supportingText = {
            if (state.value == null) {
                Text(state.constraint.localizedMessage())
            }
        },
    )
}
