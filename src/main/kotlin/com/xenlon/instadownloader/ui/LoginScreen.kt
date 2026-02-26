package com.xenlon.instadownloader.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLogin: (username: String, password: String) -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

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
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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

                Spacer(modifier = Modifier.height(4.dp))

                // Login button with gradient
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

                // Info text
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
                            Icons.Default.Shield,
                            contentDescription = null,
                            tint = AccentPurple,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Deine Daten werden nur lokal gespeichert und nie an Dritte weitergegeben.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}
