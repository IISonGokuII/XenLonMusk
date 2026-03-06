package com.xenlon.instadownloader.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xenlon.instadownloader.model.TwoFactorInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLogin: (username: String, password: String) -> Unit,
    onAnonymousMode: () -> Unit,
    onVerifyTwoFactor: (code: String) -> Unit,
    onCancelTwoFactor: () -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    isTwoFactorPending: Boolean = false,
    twoFactorInfo: TwoFactorInfo? = null,
    useSms: Boolean = false,
    onSwitchToSms: () -> Unit = {},
    onSwitchToTotp: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .padding(24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isTwoFactorPending) {
                    TwoFactorContent(
                        onVerify = onVerifyTwoFactor,
                        onCancel = onCancelTwoFactor,
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        twoFactorInfo = twoFactorInfo,
                        useSms = useSms,
                        onSwitchToSms = onSwitchToSms,
                        onSwitchToTotp = onSwitchToTotp
                    )
                } else {
                    LoginContent(
                        onLogin = onLogin,
                        onAnonymousMode = onAnonymousMode,
                        isLoading = isLoading,
                        errorMessage = errorMessage
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginContent(
    onLogin: (username: String, password: String) -> Unit,
    onAnonymousMode: () -> Unit,
    isLoading: Boolean,
    errorMessage: String?
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // Gradient header
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(InstagramPurple, InstagramPink, InstagramOrange)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.CameraAlt,
            contentDescription = "Instagram",
            modifier = Modifier.size(36.dp),
            tint = Color.White
        )
    }

    Spacer(modifier = Modifier.height(4.dp))

    Text(
        text = "InstaDownloader",
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary
    )

    Text(
        text = "Melde dich an, um Stories, Highlights\nund Profilbilder herunterzuladen",
        fontSize = 14.sp,
        color = TextSecondary,
        textAlign = TextAlign.Center,
        lineHeight = 20.sp
    )

    Spacer(modifier = Modifier.height(8.dp))

    // Username field
    OutlinedTextField(
        value = username,
        onValueChange = { username = it },
        label = { Text("Benutzername") },
        leadingIcon = {
            Icon(Icons.Default.Person, contentDescription = null, tint = TextSecondary)
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AccentPink,
            unfocusedBorderColor = Color(0xFF444458),
            focusedLabelColor = AccentPink,
            cursorColor = AccentPink,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
        ),
        enabled = !isLoading
    )

    // Password field
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Passwort") },
        leadingIcon = {
            Icon(Icons.Default.Lock, contentDescription = null, tint = TextSecondary)
        },
        trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (passwordVisible) "Passwort verbergen" else "Passwort anzeigen",
                    tint = TextSecondary
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = {
            if (username.isNotBlank() && password.isNotBlank()) onLogin(username, password)
        }),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AccentPink,
            unfocusedBorderColor = Color(0xFF444458),
            focusedLabelColor = AccentPink,
            cursorColor = AccentPink,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
        ),
        enabled = !isLoading
    )

    // Error message
    ErrorBanner(errorMessage)

    Spacer(modifier = Modifier.height(4.dp))

    // Login button
    Button(
        onClick = { onLogin(username, password) },
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        enabled = username.isNotBlank() && password.isNotBlank() && !isLoading,
        colors = ButtonDefaults.buttonColors(
            containerColor = AccentPink,
            disabledContainerColor = Color(0xFF444458)
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Wird eingeloggt...", fontSize = 16.sp)
        } else {
            Icon(Icons.Default.Login, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Anmelden", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }

    // Divider
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF444458))
        Text("  oder  ", color = TextSecondary, fontSize = 13.sp)
        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF444458))
    }

    // Anonymous mode
    OutlinedButton(
        onClick = onAnonymousMode,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        enabled = !isLoading,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentPurple),
        border = BorderStroke(1.dp, AccentPurple.copy(alpha = 0.5f))
    ) {
        Icon(Icons.Default.VisibilityOff, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Anonym fortfahren", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }

    // Info text
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = AccentPurple, modifier = Modifier.size(18.dp))
                Text(
                    text = "Deine Daten werden nur lokal gespeichert und nie an Dritte weitergegeben.",
                    color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                Text(
                    text = "Anonym: Profilbilder + gepostete Bilder (öffentlich).\nMit Login: + Stories + Highlights + eigenes Archiv.",
                    color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
private fun TwoFactorContent(
    onVerify: (code: String) -> Unit,
    onCancel: () -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    twoFactorInfo: TwoFactorInfo?,
    useSms: Boolean = false,
    onSwitchToSms: () -> Unit = {},
    onSwitchToTotp: () -> Unit = {}
) {
    var code by remember { mutableStateOf("") }

    // 2FA Icon
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Security,
            contentDescription = "2FA",
            modifier = Modifier.size(36.dp),
            tint = Color.White
        )
    }

    Spacer(modifier = Modifier.height(4.dp))

    Text(
        text = "Bestätigungscode",
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary
    )

    // Info text based on current 2FA method
    val infoText = when {
        useSms && twoFactorInfo?.obfuscatedPhone?.isNotEmpty() == true ->
            "Ein Code wurde per SMS an\n${twoFactorInfo.obfuscatedPhone} gesendet."
        useSms ->
            "Gib den SMS-Code ein, der an\ndeine Telefonnummer gesendet wurde."
        twoFactorInfo?.totpEnabled == true ->
            "Gib den 6-stelligen Code aus deiner\nAuthentificator-App ein."
        else ->
            "Gib deinen Bestätigungscode ein."
    }

    Text(
        text = infoText,
        fontSize = 14.sp,
        color = TextSecondary,
        textAlign = TextAlign.Center,
        lineHeight = 20.sp
    )

    // Method indicator
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                if (useSms) Icons.Default.Sms else Icons.Default.PhoneAndroid,
                contentDescription = null,
                tint = Color(0xFF8B5CF6),
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = if (useSms) "SMS-Code" else "Authenticator-App",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Code input field
    OutlinedTextField(
        value = code,
        onValueChange = { newValue ->
            // Only allow digits, max 8 characters (backup codes can be 8 digits)
            if (newValue.all { it.isDigit() || it == ' ' } && newValue.length <= 8) {
                code = newValue.filter { it.isDigit() }
            }
        },
        label = { Text("6-stelliger Code") },
        leadingIcon = {
            Icon(Icons.Default.Dialpad, contentDescription = null, tint = TextSecondary)
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = 22.sp,
            letterSpacing = 4.sp,
            textAlign = TextAlign.Center
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Go
        ),
        keyboardActions = KeyboardActions(onGo = {
            if (code.length >= 6) onVerify(code)
        }),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF8B5CF6),
            unfocusedBorderColor = Color(0xFF444458),
            focusedLabelColor = Color(0xFF8B5CF6),
            cursorColor = Color(0xFF8B5CF6),
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
        ),
        enabled = !isLoading
    )

    // Error message
    ErrorBanner(errorMessage)

    Spacer(modifier = Modifier.height(4.dp))

    // Verify button
    Button(
        onClick = { onVerify(code) },
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        enabled = code.length >= 6 && !isLoading,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF8B5CF6),
            disabledContainerColor = Color(0xFF444458)
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Wird überprüft...", fontSize = 16.sp)
        } else {
            Icon(Icons.Default.CheckCircle, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Bestätigen", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }

    // Switch 2FA method button
    if (twoFactorInfo?.totpEnabled == true && twoFactorInfo.smsEnabled) {
        TextButton(
            onClick = {
                code = ""
                if (useSms) onSwitchToTotp() else onSwitchToSms()
            },
            enabled = !isLoading
        ) {
            Icon(
                if (useSms) Icons.Default.PhoneAndroid else Icons.Default.Sms,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                if (useSms) "Authenticator-App verwenden" else "SMS-Code anfordern",
                fontSize = 14.sp
            )
        }
    } else if (!useSms && twoFactorInfo?.smsEnabled == true) {
        // Only SMS available as alternative (no TOTP toggle needed)
        TextButton(
            onClick = {
                code = ""
                onSwitchToSms()
            },
            enabled = !isLoading
        ) {
            Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Stattdessen SMS-Code anfordern", fontSize = 14.sp)
        }
    }

    // Cancel button
    TextButton(
        onClick = onCancel,
        enabled = !isLoading
    ) {
        Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Zurück zum Login", fontSize = 14.sp)
    }
}

@Composable
private fun ErrorBanner(errorMessage: String?) {
    AnimatedVisibility(visible = errorMessage != null) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF3D1A1A)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Error,
                    contentDescription = null,
                    tint = ErrorRed,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = errorMessage ?: "",
                    color = ErrorRed,
                    fontSize = 13.sp
                )
            }
        }
    }
}
