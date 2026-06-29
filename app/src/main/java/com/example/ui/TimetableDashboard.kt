package com.example.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.DefaultTimetable
import com.example.data.PeriodEntity
import com.example.speech.VoiceSpeechHelper
import com.example.ui.theme.*
import com.example.viewmodel.TimetableViewModel
import kotlinx.coroutines.delay
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableDashboard(
    viewModel: TimetableViewModel,
    darkThemeOverride: Boolean,
    onToggleDarkTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Observe state from ViewModel
    val currentPeriods by viewModel.currentPeriods.collectAsStateWithLifecycle()
    val selectedOwnerName by viewModel.selectedOwnerName.collectAsStateWithLifecycle()
    val selectedDay by viewModel.selectedDay.collectAsStateWithLifecycle()
    val distinctOwners by viewModel.distinctOwners.collectAsStateWithLifecycle()
    val speechEnabled by viewModel.speechPreferenceEnabled.collectAsStateWithLifecycle()

    // Local timing states
    var currentDayOfWeek by remember { mutableStateOf(viewModel.getCurrentDayOfWeekString()) }
    var minutesSinceMidnight by remember { mutableIntStateOf(viewModel.getCurrentTimeMinutes()) }

    // Run active clock sync
    LaunchedEffect(Unit) {
        while (true) {
            currentDayOfWeek = viewModel.getCurrentDayOfWeekString()
            minutesSinceMidnight = viewModel.getCurrentTimeMinutes()
            delay(10000) // Sync every 10 seconds
        }
    }

    // Voice / Speech Helpers
    var speechRecognizerStateActive by remember { mutableStateOf(false) }
    var displayedSpokenTranscription by remember { mutableStateOf("") }
    val voiceSpeechHelper = remember {
        VoiceSpeechHelper(context) {
            // TTS Completed loading callback if needed
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceSpeechHelper.destroy()
        }
    }

    // Permission launcher for audio recording
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            voiceSpeechHelper.startListening(
                onResult = { transcription ->
                    displayedSpokenTranscription = transcription
                    viewModel.handleVoiceCommand(transcription) { vocalAnnouncementString ->
                        if (speechEnabled) {
                            voiceSpeechHelper.speak(vocalAnnouncementString)
                        }
                        Toast.makeText(context, vocalAnnouncementString, Toast.LENGTH_LONG).show()
                    }
                },
                onError = { errorMessage ->
                    displayedSpokenTranscription = "Error: $errorMessage"
                    Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                },
                onStateChange = { active ->
                    speechRecognizerStateActive = active
                }
            )
        } else {
            Toast.makeText(context, "Microphone access is required for voice commands.", Toast.LENGTH_LONG).show()
        }
    }

    // Modal Control States
    var showAddPeriodDialog by remember { mutableStateOf(false) }
    var editingPeriodForDialog by remember { mutableStateOf<PeriodEntity?>(null) }
    var showFriendScheduleDialog by remember { mutableStateOf(false) }
    var showSyncImportExportDialog by remember { mutableStateOf(false) }
    var showAIUploadDialog by remember { mutableStateOf(false) }
    var ownerMenuExpanded by remember { mutableStateOf(false) }

    // AI upload specific state
    var isUploadForFriend by remember { mutableStateOf(false) }
    var friendNameInput by remember { mutableStateOf("") }
    var uploadMethodByImage by remember { mutableStateOf(true) }
    var pastedText by remember { mutableStateOf("") }
    
    val isParsingImage by viewModel.isParsingImage.collectAsStateWithLifecycle()
    val parseStatusMessage by viewModel.parseStatusMessage.collectAsStateWithLifecycle()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { selectedUri ->
            try {
                val bitmap = if (android.os.Build.VERSION.SDK_INT >= 28) {
                    val source = android.graphics.ImageDecoder.createSource(context.contentResolver, selectedUri)
                    android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                        decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                } else {
                    @Suppress("DEPRECATION")
                    android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, selectedUri)
                }

                val finalOwner = if (isUploadForFriend) {
                    if (friendNameInput.trim().isEmpty()) {
                        Toast.makeText(context, "Please enter a friend's name", Toast.LENGTH_SHORT).show()
                        return@let
                    }
                    "Friend: ${friendNameInput.trim()}"
                } else {
                    "My Schedule"
                }

                viewModel.parseAndSaveTimetable(bitmap, finalOwner)
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to read image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Calculate real-time period summaries
    val realTimeInsights = viewModel.getScheduleOverviewAtTime(
        periods = currentPeriods,
        dayOfWeek = currentDayOfWeek,
        minutesSinceMidnight = minutesSinceMidnight
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CalendarMonth,
                                contentDescription = "Calendar month icon",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "ClassConnect",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onBackground,
                                letterSpacing = (-0.5).sp
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(MaterialTheme.colorScheme.tertiary, androidx.compose.foundation.shape.CircleShape)
                                )
                                Text(
                                    text = "Stored Locally",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                },
                actions = {
                    // Manual Dark Mode override switch
                    IconButton(
                        onClick = onToggleDarkTheme,
                        modifier = Modifier.testTag("theme_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (darkThemeOverride) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = "Toggle Dark Mode",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // TTS voice preference switch
                    IconButton(
                        onClick = { viewModel.toggleSpeechPreference() },
                        modifier = Modifier.testTag("speech_preference_toggle")
                    ) {
                        Icon(
                            imageVector = if (speechEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeMute,
                            contentDescription = "Voice Preference",
                            tint = if (speechEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingPeriodForDialog = null
                    showAddPeriodDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_item_fab")
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Add Class Slot")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Dropdown Selector Item
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .shadow(2.dp, RoundedCornerShape(20.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "SELECT ACTIVE SCHEDULE PROFILE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                Button(
                                    onClick = { ownerMenuExpanded = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(text = selectedOwnerName, fontWeight = FontWeight.Bold)
                                        Icon(imageVector = Icons.Filled.ArrowDropDown, contentDescription = "Dropdown")
                                    }
                                }

                                DropdownMenu(
                                    expanded = ownerMenuExpanded,
                                    onDismissRequest = { ownerMenuExpanded = false },
                                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                                ) {
                                    distinctOwners.forEach { owner ->
                                        DropdownMenuItem(
                                            text = { Text(text = owner, fontWeight = FontWeight.Medium) },
                                            onClick = {
                                                viewModel.selectOwner(owner)
                                                ownerMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Trigger friends menu
                            IconButton(
                                onClick = { showFriendScheduleDialog = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .testTag("friend_profile_settings")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.People,
                                    contentDescription = "Manage Friends",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Sync Manager Trigger
                            IconButton(
                                onClick = { showSyncImportExportDialog = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .testTag("db_sync_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = "Sync Devices",
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // AI Scanning / Upload Image Button
                            IconButton(
                                onClick = { showAIUploadDialog = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .testTag("ai_parse_image_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = "AI Camera Parser",
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                }
            }

            // 1. Current Period Rose/Burgundy Focus Card
            item {
                Column {
                    Text(
                        text = "DYNAMIC SCHEDULE INSIGHT ($currentDayOfWeek)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                    )

                    val currentClass = realTimeInsights.current
                    val isFree = currentClass == null

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(3.dp, RoundedCornerShape(28.dp))
                            .clickable {
                                val textToSpeak = buildString {
                                    if (currentClass != null) {
                                        append("Right now, you have ${currentClass.subjectName} in room ${currentClass.roomNumber}. ")
                                    } else {
                                        append("You have no active class right now. ")
                                    }
                                    val next = realTimeInsights.next
                                    if (next != null) {
                                        append("Your next period is ${next.subjectName} in room ${next.roomNumber} starting at ${next.startTime}.")
                                    }
                                }
                                if (speechEnabled) {
                                    voiceSpeechHelper.speak(textToSpeak)
                                }
                                Toast.makeText(context, textToSpeak, Toast.LENGTH_LONG).show()
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isFree) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.secondaryContainer
                        ),
                        shape = RoundedCornerShape(28.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(
                                            if (isFree) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                                            else Color(0xFFFFD8E4) // Light rose badge
                                        )
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (isFree) "REST" else "NOW",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.sp,
                                        color = if (isFree) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF31111D)
                                    )
                                }

                                Text(
                                    text = currentClass?.let { "${it.startTime} — ${it.endTime}" } ?: "Open Session",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            if (currentClass != null) {
                                Text(
                                    text = currentClass.subjectName,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    letterSpacing = (-0.5).sp
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.LocationOn,
                                        contentDescription = "Location icon",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Lab ${currentClass.roomNumber} (South Wing)",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                val totalDuration = currentClass.endMinutes - currentClass.startMinutes
                                val elapsed = minutesSinceMidnight - currentClass.startMinutes
                                val progressFloat = if (totalDuration > 0) {
                                    (elapsed.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
                                } else 0f
                                val timeLeft = (currentClass.endMinutes - minutesSinceMidnight).coerceAtLeast(0)

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    LinearProgressIndicator(
                                        progress = { progressFloat },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(6.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        trackColor = MaterialTheme.colorScheme.outline
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = "${timeLeft}m left",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            } else {
                                Text(
                                    text = "No Class Session Now",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    letterSpacing = (-0.5).sp
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Coffee,
                                        contentDescription = "Coffee break",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Relax or study in the common rooms",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Timeline Controls (Side-by-Side 2 Column Previous & Next Grid)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val prevClass = realTimeInsights.previous
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .shadow(1.dp, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "PREVIOUS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = prevClass?.subjectName ?: "Free Slot",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = prevClass?.let { "Rm ${it.roomNumber} • ${it.startTime}" } ?: "No previous class",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }

                    val nextClass = realTimeInsights.next
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .shadow(1.dp, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "NEXT",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = nextClass?.subjectName ?: "Free Slot",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = nextClass?.let { "Rm ${it.roomNumber} • ${it.startTime}" } ?: "No future class",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // 3. Quick Announcement Box (under the insights)
            item {
                Button(
                    onClick = {
                        val announcement = buildString {
                            val current = realTimeInsights.current
                            val next = realTimeInsights.next
                            if (current != null) {
                                append("Currently you have ${current.subjectName} in Classroom ${current.roomNumber} by ${current.facultyName}. ")
                            } else {
                                append("You have no active classes at this hour. ")
                            }
                            if (next != null) {
                                append("Your next scheduled period is ${next.subjectName} in room ${next.roomNumber} at ${next.startTime}.")
                            }
                        }
                        if (speechEnabled) {
                            voiceSpeechHelper.speak(announcement)
                        }
                        Toast.makeText(context, announcement, Toast.LENGTH_LONG).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.VolumeUp, contentDescription = "Speak", modifier = Modifier.size(18.dp))
                        Text(text = "Announce Schedule Overview", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // 4. Voice Speech Command Segment matching "ASK SCHEDULE" layout
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(2.dp, RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "OFFLINE VOICE COMMANDS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (speechRecognizerStateActive) {
                                        voiceSpeechHelper.stopListening()
                                    } else {
                                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (speechRecognizerStateActive) Color.Red else MaterialTheme.colorScheme.primary,
                                    contentColor = if (speechRecognizerStateActive) Color.White else MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("voice_assistant_mic"),
                                contentPadding = PaddingValues(horizontal = 16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = if (speechRecognizerStateActive) Icons.Filled.MicOff else Icons.Filled.Mic,
                                        contentDescription = "Listen voice commands",
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (speechRecognizerStateActive) "LISTENING..." else "ASK SCHEDULE",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }

                            // Visual helper button matching the design HTML
                            IconButton(
                                onClick = { showFriendScheduleDialog = true },
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.People,
                                    contentDescription = "Friends manager button",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Transcription Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(12.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = if (speechRecognizerStateActive) "Listening active... Speak now."
                                else displayedSpokenTranscription.ifEmpty { "Try speaking: 'today', 'next', 'Monday', 'Friday', 'switch to Friend Name' or 'announce'." },
                                fontSize = 12.sp,
                                color = if (speechRecognizerStateActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (speechRecognizerStateActive) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Weekly View Card
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DAY AT A GLANCE — $selectedDay",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                        Button(
                            onClick = { viewModel.resetToDefaults() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Restore,
                                contentDescription = "Reset baseline schedule",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Day selection Chips (Clean, responsive, customized shape and borders)
                    val days = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        days.forEach { day ->
                            val isSelected = selectedDay == day
                            val isToday = currentDayOfWeek == day
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 2.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        when {
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            else -> MaterialTheme.colorScheme.surface
                                        }
                                    )
                                    .border(
                                        BorderStroke(
                                            1.dp,
                                            when {
                                                isSelected -> Color.Transparent
                                                isToday -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                            }
                                        ),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { viewModel.selectDay(day) }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = day,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.onPrimary
                                        isToday -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Render Periods list for selectedDay
            val filteredPeriods = currentPeriods.filter { it.dayOfWeek.uppercase() == selectedDay.uppercase() }
                .sortedBy { it.startMinutes }

            if (filteredPeriods.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "Empty Logo",
                            tint = Color.Gray,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No Classes Scheduled on $selectedDay",
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                items(filteredPeriods, key = { it.id }) { period ->
                    PeriodCard(
                        period = period,
                        currentMinutes = minutesSinceMidnight,
                        isToday = currentDayOfWeek.uppercase() == selectedDay.uppercase(),
                        onEdit = {
                            editingPeriodForDialog = period
                            showAddPeriodDialog = true
                        },
                        onDelete = { viewModel.deletePeriod(period.id) }
                    )
                }
            }

            // Footer Spacer
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Modal adding single Period
    if (showAddPeriodDialog) {
        AddPeriodDialog(
            ownerName = selectedOwnerName,
            editingPeriod = editingPeriodForDialog,
            onDismiss = {
                showAddPeriodDialog = false
                editingPeriodForDialog = null
            },
            onAdd = { newPeriod ->
                viewModel.insertSinglePeriod(newPeriod)
                showAddPeriodDialog = false
                editingPeriodForDialog = null
            }
        )
    }

    // Modal managing/adding friend schedules
    if (showFriendScheduleDialog) {
        FriendScheduleDialog(
            existingOwners = distinctOwners,
            onDismiss = { showFriendScheduleDialog = false },
            onAddFriend = { friendName, useBaseline ->
                val baselineList = if (useBaseline) {
                    DefaultTimetable.PRE_POPULATED_PERIODS
                } else {
                    emptyList()
                }
                viewModel.addNewFriendTimetable(friendName, baselineList)
                showFriendScheduleDialog = false
                Toast.makeText(context, "$friendName's schedule loaded successfully!", Toast.LENGTH_SHORT).show()
            },
            onDeleteFriend = { ownerToDelete ->
                viewModel.deleteOwnerSchedule(ownerToDelete)
                Toast.makeText(context, "$ownerToDelete deleted.", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Backup & Synchronize Devices dialog
    if (showSyncImportExportDialog) {
        SyncImportExportDialog(
            viewModel = viewModel,
            onDismiss = { showSyncImportExportDialog = false }
        )
    }

    if (showAIUploadDialog) {
        Dialog(onDismissRequest = { 
            if (!isParsingImage) {
                showAIUploadDialog = false 
                viewModel.clearParseStatus()
            }
        }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .shadow(12.dp, RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header Area
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = "AI Scanner logo",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    
                    Text(
                        text = "AI Timetable Configurator",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Tab selector between Image scan and Plain Text paste
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (uploadMethodByImage) MaterialTheme.colorScheme.surface
                                    else Color.Transparent
                                )
                                .clickable(enabled = !isParsingImage) { uploadMethodByImage = true }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Scan Photo",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (uploadMethodByImage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (!uploadMethodByImage) MaterialTheme.colorScheme.surface
                                    else Color.Transparent
                                )
                                .clickable(enabled = !isParsingImage) { uploadMethodByImage = false }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Paste Text",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!uploadMethodByImage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (uploadMethodByImage) {
                        Text(
                            text = "Upload a timetable image (JPEG/PNG). Gemini AI will analyze the cell layout, subject codes, faculty details, and times to instantly generate your schedule.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = "Paste unstructured schedule text (e.g. from an email, website, WhatsApp, file). Gemini AI will automatically structure the hours, room numbers, and subject details.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    // Target Profile Selector
                    Text(
                        text = "CHOOSE TARGET PROFILE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // "My Schedule" option
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (!isUploadForFriend) MaterialTheme.colorScheme.primary.copy(alpha = 0.11f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                                .border(
                                    1.dp,
                                    if (!isUploadForFriend) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable(enabled = !isParsingImage) { isUploadForFriend = false }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "My Schedule",
                                color = if (!isUploadForFriend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        // "Friend's Schedule" option
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isUploadForFriend) MaterialTheme.colorScheme.primary.copy(alpha = 0.11f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                                .border(
                                    1.dp,
                                    if (isUploadForFriend) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable(enabled = !isParsingImage) { isUploadForFriend = true }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Friend's Schedule",
                                color = if (isUploadForFriend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Input Field for Friend's Name
                    AnimatedVisibility(visible = isUploadForFriend) {
                        OutlinedTextField(
                            value = friendNameInput,
                            onValueChange = { friendNameInput = it },
                            label = { Text("Friend's Name") },
                            singleLine = true,
                            enabled = !isParsingImage,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Content Pasting for Text Method
                    AnimatedVisibility(visible = !uploadMethodByImage) {
                        OutlinedTextField(
                            value = pastedText,
                            onValueChange = { pastedText = it },
                            placeholder = { Text("Paste unstructured hours, courses, names here...") },
                            minLines = 4,
                            maxLines = 6,
                            enabled = !isParsingImage,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Status Message banner / indicator
                    parseStatusMessage?.let { msg ->
                        val isSuccess = msg.lowercase().contains("success")
                        val isFail = msg.lowercase().contains("fail") || msg.lowercase().contains("error")
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    when {
                                        isSuccess -> Color(0xFFE8F5E9)
                                        isFail -> Color(0xFFFFEBEE)
                                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                    }
                                )
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (isParsingImage) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(
                                        imageVector = when {
                                            isSuccess -> Icons.Filled.CheckCircle
                                            isFail -> Icons.Filled.Error
                                            else -> Icons.Filled.Info
                                        },
                                        contentDescription = "Status icon",
                                        tint = when {
                                            isSuccess -> Color(0xFF2E7D32)
                                            isFail -> Color(0xFFC62828)
                                            else -> MaterialTheme.colorScheme.primary
                                        },
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Text(
                                    text = msg,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = when {
                                        isSuccess -> Color(0xFF1B5E20)
                                        isFail -> Color(0xFFB71C1C)
                                        else -> MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { 
                                showAIUploadDialog = false
                                viewModel.clearParseStatus()
                            },
                            enabled = !isParsingImage,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("CLOSE")
                        }

                        Button(
                            onClick = { 
                                val finalOwner = if (isUploadForFriend) {
                                    if (friendNameInput.trim().isEmpty()) {
                                        Toast.makeText(context, "Please enter your friend's name field first.", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    "Friend: ${friendNameInput.trim()}"
                                } else {
                                    "My Schedule"
                                }

                                if (uploadMethodByImage) {
                                    imagePickerLauncher.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                } else {
                                    if (pastedText.trim().isEmpty()) {
                                        Toast.makeText(context, "Please paste schedule details first.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        viewModel.parseAndSaveTimetableFromText(pastedText, finalOwner)
                                    }
                                }
                            },
                            enabled = !isParsingImage,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (uploadMethodByImage) Icons.Filled.PhotoLibrary else Icons.Filled.Send,
                                    contentDescription = "Action button icon",
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(if (uploadMethodByImage) "SELECT" else "PARSE")
                            }
                        }
                    }
                }
            }
        }
    }
}

// Custom Period schedule card with high visual Polish matching Design HTML
@Composable
fun PeriodCard(
    period: PeriodEntity,
    currentMinutes: Int,
    isToday: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isActive = isToday && currentMinutes >= period.startMinutes && currentMinutes < period.endMinutes
    val isPast = isToday && currentMinutes >= period.endMinutes

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (isActive) 3.dp else 1.dp, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            if (isActive) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left color-strip vertical bar indicator matching the professional Design HTML spec
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(
                        when {
                            isActive -> MaterialTheme.colorScheme.secondary
                            period.isLab -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = period.subjectName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (isPast) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                    )
                    if (period.isLab) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "LAB",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                    if (isActive) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "NOW",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${period.startTime} - ${period.endTime} • Room ${period.roomNumber}",
                    fontSize = 12.sp,
                    color = if (isActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = "Instructor: ${period.facultyName.ifEmpty { "N/A" }} (${period.subjectCode})",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Normal
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Icon indicator showing lab vs lecture
            Icon(
                imageVector = if (period.isLab) Icons.Filled.GroupWork else Icons.Filled.Schedule,
                contentDescription = null,
                tint = if (isActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(20.dp)
                    .padding(end = 4.dp)
            )

            // Edit option
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(32.dp).testTag("edit_period_${period.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Edit entry",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(2.dp))

            // Remove option
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete entry",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// Dialog to add or edit a period slot
@Composable
fun AddPeriodDialog(
    ownerName: String,
    editingPeriod: PeriodEntity? = null,
    onDismiss: () -> Unit,
    onAdd: (PeriodEntity) -> Unit
) {
    val daysList = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT")
    val slotsList = listOf(
        Pair("09:00 AM - 09:55 AM", Pair(540, 595)),
        Pair("09:55 AM - 10:50 AM", Pair(595, 650)),
        Pair("10:50 AM - 11:45 AM", Pair(650, 705)),
        Pair("11:45 AM - 12:40 PM", Pair(705, 760)),
        Pair("01:20 PM - 02:15 PM", Pair(800, 855)),
        Pair("02:15 PM - 03:10 PM", Pair(855, 910)),
        Pair("03:10 PM - 04:05 PM", Pair(910, 965))
    )

    var subjectCode by remember { mutableStateOf(editingPeriod?.subjectCode ?: "") }
    var subjectName by remember { mutableStateOf(editingPeriod?.subjectName ?: "") }
    var facultyName by remember { mutableStateOf(editingPeriod?.facultyName ?: "") }
    var roomNumber by remember { mutableStateOf(editingPeriod?.roomNumber ?: "") }
    var selectedDay by remember { mutableStateOf(editingPeriod?.dayOfWeek ?: "MON") }
    var isLab by remember { mutableStateOf(editingPeriod?.isLab ?: false) }

    // Quick Slot Choice state
    var selectedQuickSlot by remember { mutableStateOf<Int?>(
        editingPeriod?.let { ep ->
            slotsList.indexOfFirst { it.second.first == ep.startMinutes }.takeIf { it >= 0 }
        }
    ) }
    var startTimeStr by remember { mutableStateOf(editingPeriod?.startTime ?: "") }
    var endTimeStr by remember { mutableStateOf(editingPeriod?.endTime ?: "") }
    var startMinutes by remember { mutableIntStateOf(editingPeriod?.startMinutes ?: 540) }
    var endMinutes by remember { mutableIntStateOf(editingPeriod?.endMinutes ?: 595) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = if (editingPeriod == null) "Add Classroom Period Slot" else "Edit Classroom Period Slot",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                item {
                    OutlinedTextField(
                        value = subjectName,
                        onValueChange = { subjectName = it },
                        label = { Text("Subject Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = subjectCode,
                        onValueChange = { subjectCode = it },
                        label = { Text("Subject Code") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = facultyName,
                        onValueChange = { facultyName = it },
                        label = { Text("Instructor Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = roomNumber,
                        onValueChange = { roomNumber = it },
                        label = { Text("Room Location") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    Text("Select Day", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        daysList.forEach { day ->
                            val active = selectedDay == day
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 2.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (active) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { selectedDay = day }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = day,
                                    fontSize = 10.sp,
                                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                item {
                    Text("Select Class Standard Hour Slot", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        slotsList.forEachIndexed { i, slot ->
                            val active = selectedQuickSlot == i
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        else Color.Transparent
                                    )
                                    .border(
                                        1.dp,
                                        if (active) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.5f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        selectedQuickSlot = i
                                        val parseArr = slot.first.split(" - ")
                                        startTimeStr = parseArr[0]
                                        endTimeStr = parseArr[1]
                                        startMinutes = slot.second.first
                                        endMinutes = slot.second.second
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Period ${i + 1}: ${slot.first}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(checked = isLab, onCheckedChange = { isLab = it })
                        Text("This is a laboratory period (longer slot)", fontSize = 12.sp)
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                if (subjectName.isNotEmpty() && startTimeStr.isNotEmpty()) {
                                    onAdd(
                                        PeriodEntity(
                                            id = editingPeriod?.id ?: 0,
                                            scheduleOwnerName = ownerName,
                                            dayOfWeek = selectedDay,
                                            subjectCode = subjectCode,
                                            subjectName = subjectName,
                                            facultyName = facultyName,
                                            roomNumber = roomNumber,
                                            startTime = startTimeStr,
                                            endTime = endTimeStr,
                                            startMinutes = startMinutes,
                                            endMinutes = endMinutes,
                                            isLab = isLab
                                        )
                                    )
                                }
                            },
                            enabled = subjectName.isNotEmpty() && startTimeStr.isNotEmpty()
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

// Dialog managing friend sheets
@Composable
fun FriendScheduleDialog(
    existingOwners: List<String>,
    onDismiss: () -> Unit,
    onAddFriend: (String, Boolean) -> Unit,
    onDeleteFriend: (String) -> Unit
) {
    var friendName by remember { mutableStateOf("") }
    var useAIMLBaseline by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Friends Timetables Manager",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                item {
                    Text(
                        text = "Add a new friend profile where you can customize or baseline with your AIML-F template timetable.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }

                item {
                    OutlinedTextField(
                        value = friendName,
                        onValueChange = { friendName = it },
                        label = { Text("Friend's Full Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { useAIMLBaseline = !useAIMLBaseline },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = useAIMLBaseline, onCheckedChange = { useAIMLBaseline = it })
                        Text("Pre-populate with AIML-F baseline template (Highly Recommended)", fontSize = 11.sp)
                    }
                }

                item {
                    Button(
                        onClick = {
                            if (friendName.trim().isNotEmpty()) {
                                onAddFriend(friendName.trim(), useAIMLBaseline)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = friendName.trim().isNotEmpty()
                    ) {
                        Text("Add Friend's Profile")
                    }
                }

                item {
                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f), thickness = 1.dp)
                    Text("Existing Friends", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }

                val friendProfiles = existingOwners.filter { it.startsWith("Friend:") }
                if (friendProfiles.isEmpty()) {
                    item {
                        Text("No friend profiles stored yet.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                } else {
                    items(friendProfiles) { friend ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = friend.replace("Friend: ", ""), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            IconButton(onClick = { onDeleteFriend(friend) }, modifier = Modifier.size(36.dp)) {
                                Icon(imageVector = Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("Close") }
                    }
                }
            }
        }
    }
}

// Offline-private JSON Backup sharing dialog
@Composable
fun SyncImportExportDialog(
    viewModel: TimetableViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var inputBackupJson by remember { mutableStateOf("") }
    val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Cross-Device Local Sync",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                item {
                    Text(
                        text = "Keep all your timetables (including friends' timetables) fully synced across your devices without public clouds. Simply export the encrypted payload, paste it on your other device, and sync!",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }

                item {
                    Button(
                        onClick = {
                            viewModel.exportAllSchedulesToJson { jsonText ->
                                val clipData = android.content.ClipData.newPlainText("Timetable Sync Code", jsonText)
                                clipboardManager.setPrimaryClip(clipData)
                                Toast.makeText(context, "Full sync payload copied to clipboard!", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.Filled.CloudDownload, contentDescription = "Export")
                            Text("Export Backup Payload Code")
                        }
                    }
                }

                item {
                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f), thickness = 1.dp)
                }

                item {
                    Text(
                        text = "Import Sync Code From Other Device",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                item {
                    OutlinedTextField(
                        value = inputBackupJson,
                        onValueChange = { inputBackupJson = it },
                        label = { Text("Paste Shared Payload Code Here") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        placeholder = { Text("Paste clipboard content...") }
                    )
                }

                item {
                    Button(
                        onClick = {
                            if (inputBackupJson.trim().isNotEmpty()) {
                                viewModel.importSchedulesFromJson(inputBackupJson.trim()) { success, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    if (success) onDismiss()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        enabled = inputBackupJson.trim().isNotEmpty()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.Filled.CloudUpload, contentDescription = "Import")
                            Text("Synchronize Local Database")
                        }
                    }
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("Close") }
                    }
                }
            }
        }
    }
}


