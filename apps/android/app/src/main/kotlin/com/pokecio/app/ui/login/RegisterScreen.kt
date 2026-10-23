package com.pokecio.app.ui.login

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.pokecio.app.R
import com.pokecio.app.ui.components.GradientButton
import com.pokecio.app.ui.theme.PockyColors
import com.pokecio.app.ui.theme.PockyIcons

private const val MIN_PASSWORD = 8

@Composable
fun RegisterScreen(
    busy: Boolean,
    error: Int?,
    server: String,
    onRegister: (username: String, password: String, inviteCode: String) -> Unit,
    onBack: () -> Unit,
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var inviteCode by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val canSubmit = !busy && username.isNotBlank() && password.length >= MIN_PASSWORD && inviteCode.isNotBlank()
    val submit = { if (canSubmit) onRegister(username, password, inviteCode) }

    Scaffold(containerColor = PockyColors.Bg) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(modifier = Modifier.widthIn(max = 420.dp)) {
                IconButton(onClick = onBack, enabled = !busy) {
                    Icon(
                        imageVector = PockyIcons.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = PockyColors.Ink,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.register_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = PockyColors.Ink,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.register_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = PockyColors.InkMuted,
            )

            Spacer(Modifier.height(28.dp))

            Column(modifier = Modifier.widthIn(max = 420.dp)) {
                FieldLabel(stringResource(R.string.login_username_label))
                LoginField(
                    value = username,
                    onValueChange = { username = it.filterNot(Char::isWhitespace).removePrefix("@").substringBefore(':') },
                    placeholder = stringResource(R.string.login_username),
                    enabled = !busy,
                    prefix = "@",
                    suffix = ":$server",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                        autoCorrectEnabled = false,
                    ),
                    modifier = Modifier.semantics { contentType = ContentType.NewUsername },
                )

                Spacer(Modifier.height(20.dp))

                FieldLabel(stringResource(R.string.login_password))
                LoginField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "••••••••",
                    enabled = !busy,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                    ),
                    trailing = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) PockyIcons.EyeOff else PockyIcons.Eye,
                                contentDescription = stringResource(
                                    if (showPassword) R.string.login_hide_password else R.string.login_show_password,
                                ),
                                tint = PockyColors.InkMuted,
                            )
                        }
                    },
                    modifier = Modifier.semantics { contentType = ContentType.NewPassword },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.register_password_hint, MIN_PASSWORD),
                    style = MaterialTheme.typography.bodySmall,
                    color = PockyColors.InkMuted,
                )

                Spacer(Modifier.height(20.dp))

                FieldLabel(stringResource(R.string.register_code_label))
                LoginField(
                    value = inviteCode,
                    onValueChange = { inviteCode = it.trim() },
                    placeholder = stringResource(R.string.register_code_placeholder),
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done,
                        autoCorrectEnabled = false,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.register_code_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = PockyColors.InkMuted,
                )

                if (error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Spacer(Modifier.height(24.dp))
                GradientButton(
                    text = stringResource(R.string.register_submit),
                    onClick = submit,
                    enabled = canSubmit,
                    busy = busy,
                )
            }
        }
    }
}
