package com.fuwaki.djifly.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fuwaki.djifly.ui.theme.AccentBlue
import com.fuwaki.djifly.ui.theme.StatusGreen
import com.fuwaki.djifly.ui.theme.StatusRed
import com.fuwaki.djifly.ui.theme.SurfaceCard
import com.fuwaki.djifly.ui.theme.SurfaceDark
import com.fuwaki.djifly.ui.theme.TextPrimary
import com.fuwaki.djifly.ui.theme.TextSecondary
import com.fuwaki.djifly.ui.viewmodel.RegisterState

/**
 * 注册页
 *
 * @param registerState 注册状态
 * @param onRegister 注册回调
 * @param onNavigateToLogin 跳转登录页
 * @param onNavigateToHome 注册成功后跳转首页
 * @param onBack 返回
 * @param onClearError 清除错误状态
 */
@Composable
fun RegisterScreen(
    registerState: RegisterState,
    onRegister: (userName: String, password: String, confirmPassword: String, djiId: String?) -> Unit = { _, _, _, _ -> },
    onNavigateToLogin: () -> Unit = {},
    onNavigateToHome: () -> Unit = {},
    onBack: () -> Unit = {},
    onClearError: () -> Unit = {}
) {
    var userName by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var djiId by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmPasswordVisible by rememberSaveable { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current

    // 实时校验
    val userNameError = when {
        userName.isNotEmpty() && userName.length < 4 -> "用户名至少 4 个字符"
        userName.isNotEmpty() && userName.length > 20 -> "用户名最多 20 个字符"
        else -> null
    }
    val passwordError = when {
        password.isNotEmpty() && password.length < 6 -> "密码至少 6 个字符"
        else -> null
    }
    val confirmPasswordError = when {
        confirmPassword.isNotEmpty() && confirmPassword != password -> "两次密码输入不一致"
        else -> null
    }

    // 注册成功后导航
    LaunchedEffect(registerState) {
        if (registerState is RegisterState.Success) {
            onNavigateToHome()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 24.dp)
        ) {
            // ── 顶部返回按钮 ──
            IconButton(
                onClick = onBack,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = TextSecondary
                )
            }

            // ── 标题区 ──
            Text(
                text = "飞手注册",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = AccentBlue
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "加入飞翼通平台",
                fontSize = 14.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── 用户名输入框 ──
            OutlinedTextField(
                value = userName,
                onValueChange = {
                    userName = it
                    onClearError()
                },
                label = { Text("用户名", color = TextSecondary) },
                placeholder = { Text("4-20 个字符", color = TextSecondary.copy(alpha = 0.5f)) },
                singleLine = true,
                isError = userNameError != null,
                supportingText = if (userNameError != null) {
                    { Text(userNameError, color = StatusRed, fontSize = 12.sp) }
                } else null,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = AccentBlue,
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                    errorBorderColor = StatusRed,
                    focusedContainerColor = SurfaceCard,
                    unfocusedContainerColor = SurfaceCard
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── 密码输入框 ──
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    onClearError()
                },
                label = { Text("密码", color = TextSecondary) },
                placeholder = { Text("至少 6 个字符", color = TextSecondary.copy(alpha = 0.5f)) },
                singleLine = true,
                isError = passwordError != null,
                supportingText = if (passwordError != null) {
                    { Text(passwordError, color = StatusRed, fontSize = 12.sp) }
                } else null,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = AccentBlue,
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                    errorBorderColor = StatusRed,
                    focusedContainerColor = SurfaceCard,
                    unfocusedContainerColor = SurfaceCard
                ),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                            tint = TextSecondary
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── 确认密码输入框 ──
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    onClearError()
                },
                label = { Text("确认密码", color = TextSecondary) },
                placeholder = { Text("再次输入密码", color = TextSecondary.copy(alpha = 0.5f)) },
                singleLine = true,
                isError = confirmPasswordError != null,
                supportingText = if (confirmPasswordError != null) {
                    { Text(confirmPasswordError, color = StatusRed, fontSize = 12.sp) }
                } else if (confirmPassword.isNotEmpty() && confirmPassword == password) {
                    { Text("密码一致", color = StatusGreen, fontSize = 12.sp) }
                } else null,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = AccentBlue,
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                    errorBorderColor = StatusRed,
                    focusedContainerColor = SurfaceCard,
                    unfocusedContainerColor = SurfaceCard
                ),
                trailingIcon = {
                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                        Icon(
                            imageVector = if (confirmPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = if (confirmPasswordVisible) "隐藏密码" else "显示密码",
                            tint = TextSecondary
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── DJI 设备 ID（可选） ──
            OutlinedTextField(
                value = djiId,
                onValueChange = { djiId = it },
                label = { Text("DJI 设备 ID", color = TextSecondary) },
                placeholder = { Text("选填", color = TextSecondary.copy(alpha = 0.5f)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = AccentBlue,
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                    focusedContainerColor = SurfaceCard,
                    unfocusedContainerColor = SurfaceCard
                ),
                supportingText = {
                    Text(
                        text = "💡 可稍后在设置中绑定",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── 服务端错误信息 ──
            AnimatedVisibility(
                visible = registerState is RegisterState.Error,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                val errorMsg = (registerState as? RegisterState.Error)?.message ?: ""
                Text(
                    text = errorMsg,
                    color = StatusRed,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    textAlign = TextAlign.Start
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── 注册按钮 ──
            val hasFormErrors = userNameError != null || passwordError != null || confirmPasswordError != null
            Button(
                onClick = {
                    focusManager.clearFocus()
                    onRegister(userName, password, confirmPassword, djiId)
                },
                enabled = registerState !is RegisterState.Loading && !hasFormErrors,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentBlue,
                    contentColor = TextPrimary,
                    disabledContainerColor = AccentBlue.copy(alpha = 0.5f),
                    disabledContentColor = TextPrimary.copy(alpha = 0.7f)
                )
            ) {
                if (registerState is RegisterState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = TextPrimary,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = if (registerState is RegisterState.Loading) "注册中..." else "注册并登录",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── 登录链接 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "已有账号？",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "去登录",
                    color = AccentBlue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { onNavigateToLogin() }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
