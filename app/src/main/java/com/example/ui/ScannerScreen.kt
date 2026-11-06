package com.example.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.model.DetectedAmount
import com.example.ui.components.AmountListBottomSheet
import com.example.ui.components.CameraPreview
import com.example.ui.components.EditAmountDialog
import com.example.ui.components.HistoryDialog
import com.example.ui.components.InteractiveBoundingBoxOverlay
import com.example.ui.components.ManualAddDialog
import com.example.ui.components.SamplePickerSheet
import com.example.ui.components.SaveCalculationDialog
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.JetBrainsMono
import com.example.viewmodel.DocumentSource
import com.example.viewmodel.ScannerViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var isAmountListSheetOpen by remember { mutableStateOf(false) }
    val amountListSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val samplePickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Camera permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onPermissionResult(isGranted)
    }

    // Gallery photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                viewModel.loadGalleryBitmap(bitmap)
            } catch (e: Exception) {
                Toast.makeText(context, "Nie udało się załadować zdjęcia", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Initial check for camera permission
    LaunchedEffect(Unit) {
        val hasCam = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionResult(hasCam)
    }

    // Handle toast messages
    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { size ->
                    viewModel.setViewportSize(size.width.toFloat(), size.height.toFloat())
                }
        ) {
            // Viewport layer (Camera or Frozen/Sample Bitmap)
            if (uiState.source == DocumentSource.LIVE_CAMERA) {
                if (uiState.isCameraPermissionGranted) {
                    CameraPreview(
                        analyzer = viewModel.analyzer,
                        isTorchEnabled = uiState.isTorchOn,
                        isFrozen = false,
                        onPreviewViewReady = { previewViewRef = it },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Camera permission rationale / fallback
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(56.dp)
                                )
                                Text(
                                    text = "Aparat do sumowania kwot",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Aby sumować kwoty na żywo z dokumentów ZUS lub rachunków, aplikacja potrzebuje dostępu do aparatu. Wszystko przetwarzane jest w 100% lokalnie na Twoim urządzeniu.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Button(
                                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("request_camera_permission_button")
                                ) {
                                    Text("Zezwól na dostęp do aparatu")
                                }
                                OutlinedButton(
                                    onClick = { viewModel.loadSampleDocument(com.example.util.SampleDocumentType.ZUS_ZDROWOTNA_TABLE) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("open_sample_picker_fallback_button")
                                ) {
                                    Text("Wypróbuj na przykładowym ZUS DRA")
                                }
                            }
                        }
                    }
                }
            } else {
                // Display Frozen / Sample bitmap
                uiState.frozenBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Zatrzymany kadr",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // Interactive OCR Bounding Box & Touch Overlay (only when active viewport is displaying content)
            if (uiState.isCameraPermissionGranted || uiState.source != DocumentSource.LIVE_CAMERA) {
                InteractiveBoundingBoxOverlay(
                    amounts = uiState.detectedAmounts,
                    isTargetFrameEnabled = uiState.isTargetFrameEnabled && uiState.source == DocumentSource.LIVE_CAMERA,
                    targetFrameRect = uiState.targetFrameRect,
                    onToggleAmount = { id -> viewModel.toggleAmountSelection(id) },
                    onSelectAmountsInRect = { rect -> viewModel.selectAmountsInRect(rect) },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Top Status & Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth(if (isLandscape) 0.62f else 1f)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mode indicator badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = when (uiState.source) {
                        DocumentSource.LIVE_CAMERA -> Color(0xCC064E3B)
                        DocumentSource.FROZEN_FRAME -> Color(0xCC1E3A8A)
                        DocumentSource.SAMPLE_DOCUMENT -> Color(0xCC312E81)
                        DocumentSource.GALLERY_PHOTO -> Color(0xCC581C87)
                    },
                    modifier = Modifier.shadow(4.dp, RoundedCornerShape(20.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when (uiState.source) {
                                        DocumentSource.LIVE_CAMERA -> EmeraldSuccess
                                        else -> Color(0xFF38BDF8)
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (uiState.source) {
                                DocumentSource.LIVE_CAMERA -> "NA ŻYWO"
                                DocumentSource.FROZEN_FRAME -> "ZAMROŻONY KADR"
                                DocumentSource.SAMPLE_DOCUMENT -> "DOKUMENT ZUS"
                                DocumentSource.GALLERY_PHOTO -> "ZDJĘCIE Z GALERII"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Action buttons: Quick Save (when frozen), Column Frame, Torch, Sample/Gallery Picker, History
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quick Save Button on Top Bar in Frozen / Photo Mode
                    if (uiState.source != DocumentSource.LIVE_CAMERA) {
                        Surface(
                            onClick = { viewModel.setSaveDialogVisible(true) },
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xEE0284C7),
                            modifier = Modifier
                                .shadow(4.dp, RoundedCornerShape(20.dp))
                                .testTag("quick_save_top_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = "Zapisz wynik",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Zapisz",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    if (uiState.source == DocumentSource.LIVE_CAMERA && uiState.isCameraPermissionGranted) {
                        // Target Column Scan Window Toggle
                        IconButton(
                            onClick = { viewModel.toggleTargetFrame() },
                            modifier = Modifier
                                .background(if (uiState.isTargetFrameEnabled) Color(0xFF0284C7) else Color(0x88000000), CircleShape)
                                .testTag("target_frame_toggle_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SelectAll,
                                contentDescription = "Celownik kolumny",
                                tint = if (uiState.isTargetFrameEnabled) Color.White else Color(0xFFCBD5E1)
                            )
                        }

                        IconButton(
                            onClick = { viewModel.toggleTorch() },
                            modifier = Modifier
                                .background(Color(0x88000000), CircleShape)
                                .testTag("torch_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (uiState.isTorchOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                                contentDescription = "Latarka",
                                tint = if (uiState.isTorchOn) Color(0xFFFBBF24) else Color.White
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.setSamplePickerVisible(true) },
                        modifier = Modifier
                            .background(Color(0x88000000), CircleShape)
                            .testTag("sample_picker_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Description,
                            contentDescription = "Przykłady / Galeria",
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = { viewModel.setHistoryDialogVisible(true) },
                        modifier = Modifier
                            .background(Color(0x88000000), CircleShape)
                            .testTag("history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Historia",
                            tint = Color.White
                        )
                    }
                }
            }

            if (isLandscape) {
                // Side Floating Dock: Total Sum Display & Controls (Landscape Mode)
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .widthIn(max = 320.dp)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xF00F172A)),
                        modifier = Modifier
                            .fillMaxSize()
                            .shadow(16.dp, RoundedCornerShape(20.dp))
                            .border(1.dp, Color(0x3338BDF8), RoundedCornerShape(20.dp))
                            .testTag("side_total_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                val formattedTotal = DetectedAmount.formatAmount(uiState.totalSum)
                                Text(
                                    text = "SUMA (${uiState.detectedAmounts.count { it.isSelected }} POZYCJI)",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.8.sp,
                                    color = Color(0xFF94A3B8),
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = formattedTotal,
                                    fontFamily = JetBrainsMono,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = if (formattedTotal.length > 12) 17.sp else 20.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.testTag("live_total_sum_text")
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                val formattedHealth = DetectedAmount.formatAmount(uiState.percentageAmount)
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF064E3B),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF34D399)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("9% Zdrowotna", formattedHealth)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "Skopiowano 9% Zdrowotną: $formattedHealth", Toast.LENGTH_SHORT).show()
                                        }
                                        .testTag("copy_percentage_button")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "9% ZDROWOTNA",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF6EE7B7)
                                            )
                                            Text(
                                                text = formattedHealth,
                                                fontFamily = JetBrainsMono,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 16.sp,
                                                color = EmeraldSuccess,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.testTag("percentage_amount_text")
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Kopiuj 9%",
                                            tint = Color(0xFF34D399),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (uiState.source == DocumentSource.LIVE_CAMERA) {
                                    Button(
                                        onClick = {
                                            val bmp = previewViewRef?.bitmap
                                            viewModel.freezeFrame(bmp)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                            .testTag("freeze_frame_button")
                                    ) {
                                        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Zatrzymaj kadr", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                    }
                                } else {
                                    Button(
                                        onClick = { viewModel.resumeLiveScanning() },
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                            .testTag("resume_camera_button")
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Wznów aparat", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val selectedCount = uiState.detectedAmounts.count { it.isSelected }
                                    OutlinedButton(
                                        onClick = { isAmountListSheetOpen = true },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .testTag("open_item_list_button")
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Pozycje ($selectedCount)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }

                                    IconButton(
                                        onClick = { viewModel.setManualAddDialogVisible(true) },
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(Color(0xFF334155), RoundedCornerShape(12.dp))
                                            .testTag("quick_manual_add_button")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Dodaj kwotę", tint = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Bottom Floating Bar: Total Sum Display & Controls (Portrait Mode)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xF00F172A)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(16.dp, RoundedCornerShape(24.dp))
                            .border(1.dp, Color(0x3338BDF8), RoundedCornerShape(24.dp))
                            .testTag("bottom_total_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            // Top row: SUMA + Big 9% SKŁADKA ZDROWOTNA with 1-tap Copy
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val formattedTotal = DetectedAmount.formatAmount(uiState.totalSum)
                                val totalFontSize = when {
                                    formattedTotal.length > 15 -> 15.sp
                                    formattedTotal.length > 11 -> 17.5.sp
                                    else -> 21.sp
                                }

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                ) {
                                    Text(
                                        text = "SUMA (${uiState.detectedAmounts.count { it.isSelected }} POZYCJI)",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 0.8.sp,
                                        color = Color(0xFF94A3B8),
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = formattedTotal,
                                        fontFamily = JetBrainsMono,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = totalFontSize,
                                        color = Color.White,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.testTag("live_total_sum_text")
                                    )
                                }

                                // Prominent 9% Health Insurance Box with 1-tap instant Copy
                                val formattedHealth = DetectedAmount.formatAmount(uiState.percentageAmount)
                                val healthFontSize = when {
                                    formattedHealth.length > 13 -> 14.sp
                                    formattedHealth.length > 10 -> 16.sp
                                    else -> 17.5.sp
                                }

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFF064E3B),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF34D399)),
                                    modifier = Modifier
                                        .clickable {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("9% Zdrowotna", formattedHealth)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "Skopiowano 9% Zdrowotną: $formattedHealth", Toast.LENGTH_SHORT).show()
                                        }
                                        .testTag("copy_percentage_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Column {
                                            Text(
                                                text = "9% ZDROWOTNA",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF6EE7B7)
                                            )
                                            Text(
                                                text = formattedHealth,
                                                fontFamily = JetBrainsMono,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = healthFontSize,
                                                color = EmeraldSuccess,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.testTag("percentage_amount_text")
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Kopiuj 9%",
                                            tint = Color(0xFF34D399),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Controls Row: Freeze/Resume, Item list drawer, Manual Add
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Primary Freeze / Resume Button
                                if (uiState.source == DocumentSource.LIVE_CAMERA) {
                                    Button(
                                        onClick = {
                                            val bmp = previewViewRef?.bitmap
                                            viewModel.freezeFrame(bmp)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                        shape = RoundedCornerShape(14.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        modifier = Modifier
                                            .weight(1.15f)
                                            .height(48.dp)
                                            .testTag("freeze_frame_button")
                                    ) {
                                        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Zatrzymaj kadr",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            maxLines = 1
                                        )
                                    }
                                } else {
                                    Button(
                                        onClick = { viewModel.resumeLiveScanning() },
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                                        shape = RoundedCornerShape(14.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        modifier = Modifier
                                            .weight(1.15f)
                                            .height(48.dp)
                                            .testTag("resume_camera_button")
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Wznów aparat",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            maxLines = 1
                                        )
                                    }
                                }

                                // Expand Itemized List Bottom Sheet
                                val selectedCount = uiState.detectedAmounts.count { it.isSelected }
                                OutlinedButton(
                                    onClick = { isAmountListSheetOpen = true },
                                    shape = RoundedCornerShape(14.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp),
                                    modifier = Modifier
                                        .weight(1.15f)
                                        .height(48.dp)
                                        .testTag("open_item_list_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Pozycje ($selectedCount)",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

                                // Quick Manual Add
                                IconButton(
                                    onClick = { viewModel.setManualAddDialogVisible(true) },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Color(0xFF334155), RoundedCornerShape(14.dp))
                                        .testTag("quick_manual_add_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Dodaj kwotę", tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Sheet: List of all recognized items
        if (isAmountListSheetOpen) {
            AmountListBottomSheet(
                amounts = uiState.detectedAmounts,
                totalSum = uiState.totalSum,
                sheetState = amountListSheetState,
                onDismiss = { isAmountListSheetOpen = false },
                onToggleItem = { id -> viewModel.toggleAmountSelection(id) },
                onSelectAll = { viewModel.selectAll() },
                onDeselectAll = { viewModel.deselectAll() },
                onDeleteItem = { id -> viewModel.deleteAmount(id) },
                onEditItem = { item -> viewModel.setEditingAmount(item) },
                onAddNewAmount = { viewModel.setManualAddDialogVisible(true) },
                onCopySummary = {
                    val summary = viewModel.generateClipboardSummary()
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Podsumowanie składek", summary))
                    Toast.makeText(context, "Skopiowano pełne zestawienie do schowka", Toast.LENGTH_SHORT).show()
                },
                onSaveToHistory = { viewModel.setSaveDialogVisible(true) }
            )
        }

        // Bottom Sheet: Sample Document Picker
        if (uiState.showSamplePicker) {
            SamplePickerSheet(
                sheetState = samplePickerSheetState,
                onDismiss = { viewModel.setSamplePickerVisible(false) },
                onSelectSample = { type ->
                    viewModel.setSamplePickerVisible(false)
                    viewModel.loadSampleDocument(type)
                },
                onPickGalleryImage = {
                    viewModel.setSamplePickerVisible(false)
                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            )
        }

        // Dialog: Manual Add
        if (uiState.showManualAddDialog) {
            ManualAddDialog(
                onDismiss = { viewModel.setManualAddDialogVisible(false) },
                onConfirm = { value, label -> viewModel.addManualAmount(value, label) }
            )
        }

        // Dialog: Edit Item
        uiState.editingAmount?.let { amountToEdit ->
            EditAmountDialog(
                amount = amountToEdit,
                onDismiss = { viewModel.setEditingAmount(null) },
                onConfirm = { newValue, newLabel ->
                    viewModel.editAmount(amountToEdit.id, newValue, newLabel)
                }
            )
        }

        // Dialog: Save Calculation
        if (uiState.showSaveDialog) {
            SaveCalculationDialog(
                totalAmount = uiState.totalSum,
                itemCount = uiState.detectedAmounts.count { it.isSelected },
                onDismiss = { viewModel.setSaveDialogVisible(false) },
                onConfirm = { title -> viewModel.saveCurrentCalculation(title) }
            )
        }

        // Dialog: History
        if (uiState.showHistoryDialog) {
            HistoryDialog(
                history = uiState.savedHistory,
                onDismiss = { viewModel.setHistoryDialogVisible(false) },
                onRestore = { calc -> viewModel.restoreCalculation(calc) },
                onDelete = { id -> viewModel.deleteHistoryItem(id) }
            )
        }
    }
}
