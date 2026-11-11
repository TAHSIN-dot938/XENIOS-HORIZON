package com.tahsin.thehorizon.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.tahsin.thehorizon.ui.theme.HorizonColors
import com.tahsin.thehorizon.ui.theme.Spacing

@Composable
fun HorizonTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
) {
    var revealed by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = if (error != null) { { Text(error) } } else null,
        visualTransformation = if (isPassword && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
            imeAction = imeAction,
        ),
        trailingIcon = if (isPassword) {
            { TextButton(onClick = { revealed = !revealed }) { Text(if (revealed) "HIDE" else "SHOW", style = MaterialTheme.typography.labelSmall) } }
        } else null,
        shape = RoundedCornerShape(Spacing.CornerControl),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = HorizonColors.Purple,
            unfocusedBorderColor = HorizonColors.GlassBorder,
            errorBorderColor = HorizonColors.Danger,
            focusedContainerColor = HorizonColors.GlassFill,
            unfocusedContainerColor = HorizonColors.GlassFill,
            errorContainerColor = HorizonColors.GlassFill,
            focusedLabelColor = HorizonColors.PurpleSoft,
            unfocusedLabelColor = HorizonColors.TextSecondary,
            cursorColor = HorizonColors.Purple,
            focusedTextColor = HorizonColors.TextPrimary,
            unfocusedTextColor = HorizonColors.TextPrimary,
        ),
    )
}
