package com.pokecio.app.ui.login

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.pokecio.app.R
import com.pokecio.app.ui.components.ControlShape
import com.pokecio.app.ui.components.GradientButton
import com.pokecio.app.ui.theme.PockyColors
import com.pokecio.app.ui.theme.PockyIcons

private const val SERVER_NAME = "pokecio.com"

@Composable
fun LoginScreen(
    busy: Boolean,
    error: Int?,
    onLogin: (username: String, password: String) -> Unit,
    onQrLogin: (() -> Unit)? = null,
    onRegister: (() -> Unit)? = null,
    onChangeServer: (() -> Unit)? = null,
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val canSubmit = !busy && username.isNotBlank() && password.isNotEmpty()
    val submit = { if (canSubmit) onLogin(username, password) }

    Scaffold(containerColor = PockyColors.Bg) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PockyLogo()

            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = PockyColors.Ink,
                )
                Spacer(Modifier.width(10.dp))
                BetaBadge()
            }

            Spacer(Modifier.height(8.dp))
            Tagline()
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.login_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = PockyColors.InkMuted,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(32.dp))

            Column(modifier = Modifier.widthIn(max = 420.dp)) {
                FieldLabel(stringResource(R.string.login_username_label))
                LoginField(
                    value = username,
                    onValueChange = { username = localpart(it) },
                    placeholder = stringResource(R.string.login_username),
                    enabled = !busy,
                    prefix = "@",
                    suffix = ":$SERVER_NAME",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                        autoCorrectEnabled = false,
                    ),
                    modifier = Modifier.semantics { contentType = ContentType.Username },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.login_username_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = PockyColors.InkMuted,
                )

                Spacer(Modifier.height(20.dp))

                FieldLabel(stringResource(R.string.login_password))
                LoginField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "••••••••",
                    enabled = !busy,
                    visualTransformation = if (showPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
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
                    modifier = Modifier.semantics { contentType = ContentType.Password },
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
                    text = stringResource(R.string.login_submit),
                    onClick = submit,
                    enabled = canSubmit,
                    busy = busy,
                )

                Spacer(Modifier.height(20.dp))
                OrDivider()
                Spacer(Modifier.height(20.dp))

                OutlinedButton(
                    onClick = { onQrLogin?.invoke() },
                    enabled = !busy && onQrLogin != null,
                    shape = ControlShape,
                    border = BorderStroke(1.dp, PockyColors.InkMuted.copy(alpha = 0.25f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = PockyColors.Ink,
                        disabledContentColor = PockyColors.InkMuted.copy(alpha = 0.6f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                ) {
                    Icon(PockyIcons.Qr, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.login_qr))
                }
            }

            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.login_no_account),
                    style = MaterialTheme.typography.bodyLarge,
                    color = PockyColors.InkMuted,
                )
                Spacer(Modifier.width(6.dp))
                LinkText(stringResource(R.string.login_register), onRegister)
            }

            Spacer(Modifier.height(12.dp))
            ServerChip(onChangeServer)
        }
    }
}

@Composable
private fun BetaBadge() {
    Text(
        text = stringResource(R.string.login_beta),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = PockyColors.Gold,
        modifier = Modifier
            .background(PockyColors.Gold.copy(alpha = 0.14f), CircleShape)
            .border(1.dp, PockyColors.Gold.copy(alpha = 0.45f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
private fun Tagline() {
    val full = stringResource(R.string.login_tagline)
    val highlight = stringResource(R.string.login_tagline_highlight)
    val start = full.indexOf(highlight)

    val text = buildAnnotatedString {
        if (start < 0) {
            append(full)
        } else {
            append(full.substring(0, start))
            withStyle(SpanStyle(color = PockyColors.AccentSoft)) { append(highlight) }
            append(full.substring(start + highlight.length))
        }
    }

    Text(
        text = text,
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        color = PockyColors.Ink,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = PockyColors.InkMuted,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun OrDivider() {
    val line = PockyColors.InkMuted.copy(alpha = 0.2f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = line)
        Text(
            text = stringResource(R.string.login_or),
            style = MaterialTheme.typography.labelMedium,
            color = PockyColors.InkMuted,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(Modifier.weight(1f), color = line)
    }
}

@Composable
private fun ServerChip(onChange: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(PockyColors.Surface, CircleShape)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            text = stringResource(R.string.login_server),
            style = MaterialTheme.typography.bodyMedium,
            color = PockyColors.InkMuted,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = SERVER_NAME,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = PockyColors.Ink,
        )
        Spacer(Modifier.width(10.dp))
        LinkText(stringResource(R.string.login_server_change), onChange, MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LinkText(
    text: String,
    onClick: (() -> Unit)?,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    Text(
        text = text,
        style = style,
        fontWeight = FontWeight.Bold,
        color = PockyColors.AccentSoft,
        modifier = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier,
    )
}

private fun localpart(input: String): String =
    input.filterNot(Char::isWhitespace)
        .removePrefix("@")
        .substringBefore(':')
