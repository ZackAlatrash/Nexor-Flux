package com.zack.recomptracker.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.zack.recomptracker.data.preferences.ActivityLevel
import com.zack.recomptracker.data.preferences.BiologicalSex
import com.zack.recomptracker.data.preferences.FitnessGoal
import com.zack.recomptracker.data.preferences.UserProfilePreferences
import com.zack.recomptracker.data.preferences.ageYears
import com.zack.recomptracker.data.preferences.displayName
import com.zack.recomptracker.ui.FloatingNavHeight
import com.zack.recomptracker.ui.component.FrostedCard
import com.zack.recomptracker.ui.component.GlassInputField
import com.zack.recomptracker.ui.component.ScoreStepper
import com.zack.recomptracker.ui.component.SectionLabel
import com.zack.recomptracker.ui.component.SubScreenHeader
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.CornerSmall
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val DOB_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onBack: () -> Unit,
    onOpenBody: () -> Unit,
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val nameInput by viewModel.nameInput.collectAsStateWithLifecycle()
    val heightInput by viewModel.heightInput.collectAsStateWithLifecycle()
    val currentWeightKg by viewModel.currentWeightKg.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current

    val ambientOrb1 = remember(accent.accent) {
        Brush.radialGradient(listOf(accent.accent.copy(alpha = 0.15f), Color.Transparent))
    }
    val ambientOrb2 = remember(accent.accentLight) {
        Brush.radialGradient(listOf(accent.accentLight.copy(alpha = 0.08f), Color.Transparent))
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.update(profile.copy(profilePhotoUri = uri.toString()))
        }
    }

    // ── Picker sheet state ────────────────────────────────────────────────────
    var showGoalSheet by remember { mutableStateOf(false) }
    var showActivitySheet by remember { mutableStateOf(false) }
    var showSexSheet by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .size(320.dp)
                .offset(x = (-80).dp, y = (-60).dp)
                .background(ambientOrb1),
        )
        Box(
            modifier = Modifier
                .size(240.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 60.dp, y = (-180).dp)
                .background(ambientOrb2),
        )

        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = FloatingNavHeight + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ── Header ────────────────────────────────────────────────────────
            item {
                SubScreenHeader(title = "Profile", onBack = onBack)
            }

            // ── Avatar + name ─────────────────────────────────────────────────
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AvatarPicker(
                        photoUri = profile.profilePhotoUri,
                        onClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                    )
                    GlassInputField(
                        label = "Name",
                        value = nameInput,
                        onValueChange = viewModel::setName,
                        keyboardType = KeyboardType.Text,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // ── Current weight (read-only) ────────────────────────────────────
            item {
                FrostedCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onOpenBody,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            SectionLabel("Current weight")
                            Text(
                                text = currentWeightKg?.let { "%.1f kg".format(it) } ?: "—",
                                style = AppType.statValue,
                                color = appColors.textPrimary,
                            )
                        }
                        Text(
                            text = "View",
                            style = AppType.body.copy(fontWeight = FontWeight.SemiBold),
                            color = accent.inkLight,
                        )
                    }
                }
            }

            // ── Plan inputs ───────────────────────────────────────────────────
            item { SectionLabel("Plan inputs") }
            item {
                FrostedCard {
                    PickerRow(
                        label = "Goal",
                        value = profile.goal?.displayName() ?: "Not set",
                        subtitle = profile.goal?.shortDesc() ?: "Tap to choose a goal",
                        onClick = { showGoalSheet = true },
                    )
                    Spacer(Modifier.height(8.dp))
                    PickerRow(
                        label = "Activity level",
                        value = profile.activityLevel?.displayName() ?: "Not set",
                        subtitle = profile.activityLevel?.shortDesc() ?: "Tap to choose your activity",
                        onClick = { showActivitySheet = true },
                    )
                    Spacer(Modifier.height(16.dp))
                    ScoreStepper(
                        label = "Gym sessions / week",
                        value = profile.weeklyGymSessions ?: 0,
                        onValueChange = { viewModel.update(profile.copy(weeklyGymSessions = it)) },
                        range = 0..7,
                    )
                }
            }

            // ── About you ─────────────────────────────────────────────────────
            item { SectionLabel("About you") }
            item {
                FrostedCard {
                    PickerRow(
                        label = "Biological sex",
                        value = profile.biologicalSex?.displayName() ?: "Not set",
                        subtitle = null,
                        onClick = { showSexSheet = true },
                    )
                    Spacer(Modifier.height(8.dp))
                    val dob = profile.birthDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    PickerRow(
                        label = "Date of birth",
                        value = if (dob != null) {
                            val age = profile.ageYears()
                            dob.format(DOB_FMT) + (age?.let { " · $it yrs" } ?: "")
                        } else {
                            "Set"
                        },
                        subtitle = null,
                        onClick = { showDatePicker = true },
                    )
                    Spacer(Modifier.height(16.dp))
                    GlassInputField(
                        label = "Height",
                        value = heightInput,
                        onValueChange = viewModel::setHeight,
                        unit = "cm",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    // ── Goal sheet ────────────────────────────────────────────────────────────
    if (showGoalSheet) {
        OptionSheet(
            title = "Goal",
            onDismiss = { showGoalSheet = false },
        ) {
            FitnessGoal.entries.forEach { g ->
                ProfileOptionRow(
                    label = g.displayName(),
                    subtitle = g.shortDesc(),
                    selected = profile.goal == g,
                    onClick = {
                        viewModel.update(profile.copy(goal = g))
                        showGoalSheet = false
                    },
                )
            }
        }
    }

    // ── Activity sheet ──────────────────────────────────────────────────────────
    if (showActivitySheet) {
        OptionSheet(
            title = "Activity level",
            onDismiss = { showActivitySheet = false },
        ) {
            ActivityLevel.entries.forEach { a ->
                ProfileOptionRow(
                    label = a.displayName(),
                    subtitle = a.shortDesc(),
                    selected = profile.activityLevel == a,
                    onClick = {
                        viewModel.update(profile.copy(activityLevel = a))
                        showActivitySheet = false
                    },
                )
            }
        }
    }

    // ── Sex sheet ─────────────────────────────────────────────────────────────
    if (showSexSheet) {
        OptionSheet(
            title = "Biological sex",
            onDismiss = { showSexSheet = false },
        ) {
            BiologicalSex.entries.forEach { s ->
                ProfileOptionRow(
                    label = s.displayName(),
                    subtitle = null,
                    selected = profile.biologicalSex == s,
                    onClick = {
                        viewModel.update(profile.copy(biologicalSex = s))
                        showSexSheet = false
                    },
                )
            }
        }
    }

    // ── Date-of-birth picker ──────────────────────────────────────────────────
    if (showDatePicker) {
        val initialMillis = profile.birthDate
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        val picked = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                            .toString()
                        viewModel.update(profile.copy(birthDate = picked))
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

// ── Avatar ──────────────────────────────────────────────────────────────────

@Composable
private fun AvatarPicker(
    photoUri: String?,
    onClick: () -> Unit,
) {
    val accent = LocalAppAccent.current
    val placeholderBrush = remember(accent.accent, accent.accentLight) {
        Brush.linearGradient(listOf(accent.accent.copy(alpha = 0.45f), accent.accentLight.copy(alpha = 0.25f)))
    }
    Box(contentAlignment = Alignment.BottomEnd) {
        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(CircleShape)
                .background(placeholderBrush)
                .border(1.dp, Color(0x33FFFFFF), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (photoUri != null) {
                AsyncImage(
                    model = photoUri,
                    contentDescription = "Profile photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                )
            } else {
                Icon(
                    Icons.Rounded.Person,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.size(44.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(accent.accent)
                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.PhotoCamera,
                contentDescription = "Change photo",
                tint = accent.onAccent,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// ── Picker row (tappable; shows current value) ────────────────────────────────

@Composable
private fun PickerRow(
    label: String,
    value: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CornerSmall))
            .border(1.dp, appColors.frostedBorder, RoundedCornerShape(CornerSmall))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SectionLabel(label)
            Text(
                text = value,
                style = AppType.cardTitle,
                color = appColors.textPrimary,
            )
            if (subtitle != null) {
                Text(text = subtitle, style = AppType.label, color = appColors.textMuted)
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = accent.inkLight,
            modifier = Modifier.size(20.dp),
        )
    }
}

// ── Option bottom sheet ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val appColors = LocalAppColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = AppType.cardTitle.copy(fontWeight = FontWeight.ExtraBold),
                color = appColors.textPrimary,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            content()
        }
    }
}

// ── Profile option row (selectable row inside the option bottom sheets) ───────

@Composable
private fun ProfileOptionRow(
    label: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    val bgColor = if (selected) accent.accent.copy(alpha = 0.10f) else Color.Transparent
    val borderColor = if (selected) accent.accent.copy(alpha = 0.30f) else appColors.cardBorder

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CornerSmall))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(CornerSmall))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = label,
                style = AppType.body.copy(fontWeight = FontWeight.SemiBold),
                color = if (selected) appColors.textPrimary else appColors.textDim,
            )
            if (subtitle != null) {
                Text(text = subtitle, style = AppType.label, color = appColors.textMuted)
            }
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent.accentLight),
            )
        }
    }
}

// ── Goal / activity descriptions ──────────────────────────────────────────────

private fun FitnessGoal.shortDesc(): String = when (this) {
    FitnessGoal.AGGRESSIVE_CUT  -> "~500+ kcal deficit"
    FitnessGoal.MODERATE_CUT    -> "~300–500 kcal deficit"
    FitnessGoal.MINI_CUT        -> "~150–200 kcal deficit"
    FitnessGoal.RECOMP          -> "Maintain and recompose"
    FitnessGoal.LEAN_BULK       -> "~100–200 kcal surplus"
    FitnessGoal.MODERATE_BULK   -> "~300–500 kcal surplus"
    FitnessGoal.AGGRESSIVE_BULK -> "~500+ kcal surplus"
}

private fun ActivityLevel.shortDesc(): String = when (this) {
    ActivityLevel.SEDENTARY          -> "Desk job, little exercise"
    ActivityLevel.LIGHTLY_ACTIVE     -> "1–3 workout days / week"
    ActivityLevel.MODERATELY_ACTIVE  -> "3–5 workout days / week"
    ActivityLevel.VERY_ACTIVE        -> "6–7 hard workout days / week"
}
