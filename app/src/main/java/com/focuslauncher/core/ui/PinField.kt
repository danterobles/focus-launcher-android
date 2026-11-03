package com.focuslauncher.core.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.focuslauncher.core.security.PinPolicy

/**
 * Campo de PIN: oculto, teclado numérico de contraseña (sin sugerencias ni portapapeles), solo
 * dígitos ASCII y como máximo 12. El texto vive en estado local de la composición (`remember`,
 * nunca `rememberSaveable`): no se restaura al rotar ni tras morir el proceso.
 */
@Composable
fun PinField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
) {
    OutlinedTextField(
        value = value,
        // Solo dígitos ASCII 0–9 y como máximo 12: lo demás (letras, dígitos Unicode, espacios) se descarta.
        onValueChange = { new -> onValueChange(new.filter { it in '0'..'9' }.take(PinPolicy.MAX_LENGTH)) },
        modifier = modifier,
        enabled = enabled,
        singleLine = true,
        label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = imeAction, autoCorrectEnabled = false),
    )
}
