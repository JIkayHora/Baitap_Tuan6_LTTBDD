package com.example.btt6_capquyen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.btt6_capquyen.ui.theme.Btt6_capquyenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Btt6_capquyenTheme {
                Surface( modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background){
                    PermissionFlowScreen()
                }
            }
        }
    }
}
data class PermissionStepData(
    val type: String,
    val title: String,
    val description: String,
    val confirmButtonText: String,
    val permissionString: String
)
@Composable
fun PermissionFlowScreen() {
    val context = LocalContext.current
    val orangePrimary = Color(0xFFF0783E)
    val lightMintBg = Color(0xFFEFFBF2)

    val steps = remember {
        listOf(
            PermissionStepData(
                type = "location",
                title = "Location",
                description = "Allow maps to access your location while you use the app?",
                confirmButtonText = "Allow",
                permissionString = Manifest.permission.ACCESS_FINE_LOCATION
            ),
            PermissionStepData(
                type = "notification",
                title = "Notification",
                description = "Please enable notifications to receive updates and reminders",
                confirmButtonText = "Turn on",
                permissionString = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.POST_NOTIFICATIONS
                } else {
                    ""
                }
            ),
            PermissionStepData(
                type = "camera",
                title = "Camera",
                description = "We need access to your camera to scan QR codes",
                confirmButtonText = "Turn on",
                permissionString = Manifest.permission.CAMERA
            )
        )
    }
    var currentStepIndex by remember { mutableIntStateOf(0) }
    var showDialog by remember { mutableStateOf(true) }
    val nextStep = {
        if (currentStepIndex < steps.size - 1) {
            currentStepIndex++
        } else {
            showDialog = false
            Toast.makeText(context, "Đã hoàn tất cả 3 bước cấp quyền!", Toast.LENGTH_SHORT).show()
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val currentTitle = steps[currentStepIndex].title
        val msg = if (isGranted) "Đã cho phép $currentTitle" else "Đã từ chối $currentTitle"
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        nextStep()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF222222))
            .systemBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.85f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("VI",
                fontSize = 11.sp,
                color = Color.DarkGray,
                fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(orangePrimary)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("ENG",
                    fontSize = 11.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold)
            }
        }
        if (!showDialog) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Đã hoàn thành luồng hỏi quyền",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        currentStepIndex = 0
                        showDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = orangePrimary)
                ) {
                    Text("Xem lại từ đầu", color = Color.White)
                }
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { Toast.makeText(context, "Nhấn Login", Toast.LENGTH_SHORT).show() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = orangePrimary)
            ) {
                Text("Login",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = { Toast.makeText(context, "Nhấn Continue without account", Toast.LENGTH_SHORT).show() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color.Gray),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White.copy(alpha = 0.9f)
                )
            ) {
                Text("Continue without account",
                    color = Color.Gray,
                    fontSize = 15.sp)
            }
        }
        if (showDialog) {
            val currentStep = steps[currentStepIndex]

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(lightMintBg),
                            contentAlignment = Alignment.Center
                        ) {
                            PermissionStepIllustration(type = currentStep.type, orangeColor = orangePrimary)
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = currentStep.title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1F2937)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = currentStep.description,
                            fontSize = 14.sp,
                            color = Color(0xFF6B7280),
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(28.dp))
                        Button(
                            onClick = {
                                val perm = currentStep.permissionString
                                if (perm.isBlank()) {
                                    Toast.makeText(context, "Thiết bị đã hỗ trợ sẵn Notification", Toast.LENGTH_SHORT).show()
                                    nextStep()
                                } else {
                                    val isGranted = ContextCompat.checkSelfPermission(
                                        context,
                                        perm
                                    ) == PackageManager.PERMISSION_GRANTED

                                    if (isGranted) {
                                        Toast.makeText(context, "Quyền ${currentStep.title} đã được cấp!", Toast.LENGTH_SHORT).show()
                                        nextStep()
                                    } else {
                                        permissionLauncher.launch(perm)
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = orangePrimary)
                        ) {
                            Text(
                                text = currentStep.confirmButtonText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { nextStep() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, orangePrimary)
                        ) {
                            Text(
                                text = "Skip for now",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = orangePrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionStepIllustration(type: String, orangeColor: Color) {
    when (type) {
        "location" -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Location",
                    tint = orangeColor,
                    modifier = Modifier.size(48.dp)
                )
                Canvas(modifier = Modifier.size(width = 28.dp, height = 8.dp)) {
                    drawOval(
                        color = Color(0xFF4CAF50),
                        topLeft = Offset.Zero,
                        size = Size(size.width, size.height)
                    )
                }
            }
        }
        "notification" -> {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = "Notification",
                tint = Color(0xFFFFB300),
                modifier = Modifier
                    .size(50.dp)
                    .rotate(-20f)
            )
        }
        "camera" -> {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(orangeColor),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .border(3.dp, Color.White, CircleShape)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    Btt6_capquyenTheme {
        PermissionFlowScreen()
    }
}