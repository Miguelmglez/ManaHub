package com.mmg.manahub.feature.profile.presentation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.MagicAlertDialog
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.components.MagicLoadingSize
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.core.util.recordSafeNonFatal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// SECURITY: The feedback recipient address is kept as a private compile-time
// constant. It must NOT be placed in strings.xml (visible in decompiled APK
// resources), passed as a composable parameter, logged via Log.*, or stored
// in any shared/exported state.
// ---------------------------------------------------------------------------
private const val FEEDBACK_EMAIL = "manahub@gmx.net"

/** Maximum allowed message length in characters. */
private const val MAX_CHARS = 2000

// SECURITY: Maximum attachment size enforced via ParcelFileDescriptor.statSize
// (actual OS-reported fstat size), NOT from ContentResolver Bundle extras which
// a malicious ContentProvider could fake.
private const val MAX_IMAGE_BYTES = 10L * 1024L * 1024L // 10 MB

// SECURITY: Accepted MIME types validated against ContentResolver.getType(),
// which reads the type from the actual file content / provider metadata —
// NOT from the file extension, which can be trivially spoofed by renaming any
// file (e.g. "malware.exe" → "photo.jpg" would still be rejected here).
private val ALLOWED_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/gif")

/**
 * Validates that the [uri] refers to an acceptable image file. Performs provider IPC, so it must run
 * off the main thread.
 *
 * - MIME type: read from [android.content.ContentResolver.getType] (not from file extension).
 * - File size: measured via [android.os.ParcelFileDescriptor.statSize] (not from metadata extras). An
 *   unknown size (-1) is rejected, otherwise it would bypass the cap.
 *
 * @return true if the file passes all checks; false otherwise, including when the provider throws
 *   (for example a cloud-only document).
 */
private fun isImageSafe(context: android.content.Context, uri: Uri): Boolean = try {
    val mimeType = context.contentResolver.getType(uri)
    if (mimeType == null || mimeType !in ALLOWED_MIME_TYPES) {
        false
    } else {
        val size = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
        size in 0..MAX_IMAGE_BYTES
    }
} catch (e: Exception) {
    recordSafeNonFatal("feedback_attachment_check_failed", e)
    false
}

/**
 * A [ModalBottomSheet] that lets the user compose and send feedback via email.
 *
 * Drag and scrim taps cannot dismiss it while a draft exists; system Back then asks to discard it.
 * The draft survives configuration changes. The recipient address is a private constant and is never
 * surfaced in the UI.
 *
 * @param onDismiss Called after the sheet has been fully hidden.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val toastState = rememberMagicToastState()

    var messageText by rememberSaveable { mutableStateOf("") }
    var attachedImageUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var showEmptyError by rememberSaveable { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var isCheckingAttachment by remember { mutableStateOf(false) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }

    val hasDraft = messageText.isNotBlank() || attachedImageUri != null
    val allowHide by rememberUpdatedState(!hasDraft)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || allowHide },
    )

    /** Hides the sheet and notifies the caller. */
    fun dismiss() {
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    /** Resets all sheet state and dismisses. */
    fun resetAndDismiss() {
        messageText = ""
        attachedImageUri = null
        showEmptyError = false
        isSending = false
        dismiss()
    }

    // ── Image pickers ─────────────────────────────────────────────────────────

    val invalidFileMessage = stringResource(R.string.feedback_invalid_file)
    val chooserFailedMessage = stringResource(R.string.error_unknown)

    /** Validates a picked URI off the main thread, then attaches it or shows an error. */
    fun onImagePicked(uri: Uri?) {
        if (uri == null) return
        isCheckingAttachment = true
        scope.launch {
            val safe = withContext(Dispatchers.IO) { isImageSafe(context, uri) }
            isCheckingAttachment = false
            if (safe) {
                attachedImageUri = uri
            } else {
                toastState.show(message = invalidFileMessage, type = MagicToastType.ERROR)
            }
        }
    }

    // API 33+ — Photo Picker (no permission required)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> onImagePicked(uri) }

    // API 29–32 — GetContent fallback (no permission required for content URIs)
    val getContentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri -> onImagePicked(uri) }

    /** Opens the appropriate image picker for the current API level. */
    fun openImagePicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        } else {
            getContentLauncher.launch("image/*")
        }
    }

    // ── Send action ───────────────────────────────────────────────────────────

    fun sendFeedback() {
        if (messageText.isBlank()) {
            showEmptyError = true
            return
        }
        isSending = true

        // SECURITY NOTES:
        // 1. When no attachment is present, ACTION_SENDTO + "mailto:" is used.
        //    This restricts the chooser to email-only apps (apps that handle
        //    mailto: URIs), unlike ACTION_SEND which can match any sharing app.
        // 2. When an attachment is present, ACTION_SEND is required (ACTION_SENDTO
        //    does not support EXTRA_STREAM). The MIME type is set to the actual
        //    content type reported by ContentResolver — not hardcoded to
        //    "message/rfc822", which could cause some email clients to silently
        //    drop the attachment when the declared type doesn't match the file.
        // 3. FLAG_GRANT_READ_URI_PERMISSION is mandatory when attaching a
        //    content:// URI. Without it, the resolved email app (different UID)
        //    will receive a SecurityException when it tries to open the stream.
        // 4. The recipient address (FEEDBACK_EMAIL) and message body are never
        //    logged. EXTRA_TEXT is plain text — no injection risk in email clients.
        // 5. EXIF metadata: the raw content URI is passed directly. Images may
        //    contain GPS coordinates, device model, and timestamps. This is
        //    intentional and disclosed in the Privacy Policy — the user explicitly
        //    chose to attach the file and must tap "Send" in their email app.
        val subject = "ManaHub Feedback (${BuildConfig.VERSION_NAME} - ${BuildConfig.VERSION_CODE})"
        val imageUri = attachedImageUri
        val intent = if (imageUri != null) {
            val attachmentMime = runCatching { context.contentResolver.getType(imageUri) }.getOrNull() ?: "image/*"
            Intent(Intent.ACTION_SEND).apply {
                type = attachmentMime
                putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, messageText)
                putExtra(Intent.EXTRA_STREAM, imageUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, messageText)
            }
        }

        try {
            context.startActivity(
                Intent.createChooser(intent, context.getString(R.string.feedback_chooser_title)),
            )
        } catch (e: ActivityNotFoundException) {
            recordSafeNonFatal("feedback_chooser_unavailable", e)
            isSending = false
            toastState.show(message = chooserFailedMessage, type = MagicToastType.ERROR)
            return
        }

        resetAndDismiss()
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = mc.backgroundSecondary,
        dragHandle = null, // we provide our own header row
    ) {
        BackHandler(enabled = hasDraft) { showDiscardDialog = true }

        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.lg)
                    .padding(bottom = spacing.xl)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {

                // ── Header ────────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { if (hasDraft) showDiscardDialog = true else dismiss() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.feedback_cancel),
                            tint = mc.textSecondary,
                        )
                    }
                    Text(
                        text = stringResource(R.string.feedback_title),
                        style = ty.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = mc.textPrimary,
                    )
                }

                // ── Text field ────────────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { newValue ->
                            if (newValue.length <= MAX_CHARS) {
                                messageText = newValue
                                if (newValue.isNotBlank()) showEmptyError = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                text = stringResource(R.string.feedback_hint),
                                style = ty.bodyMedium,
                                color = mc.textDisabled,
                            )
                        },
                        minLines = 4,
                        maxLines = Int.MAX_VALUE,
                        isError = showEmptyError,
                        supportingText = if (showEmptyError) {
                            {
                                Text(
                                    text = stringResource(R.string.feedback_empty_error),
                                    color = mc.lifeNegative,
                                    style = ty.labelSmall,
                                )
                            }
                        } else null,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = mc.primaryAccent,
                            unfocusedBorderColor = mc.surfaceVariant,
                            focusedTextColor = mc.textPrimary,
                            unfocusedTextColor = mc.textPrimary,
                            cursorColor = mc.primaryAccent,
                            focusedContainerColor = mc.surface,
                            unfocusedContainerColor = mc.surface,
                        ),
                        shape = CardShape,
                        textStyle = ty.bodyMedium.copy(color = mc.textPrimary),
                    )

                    Text(
                        text = stringResource(R.string.feedback_char_count, messageText.length),
                        style = ty.labelSmall,
                        color = mc.textDisabled,
                        modifier = Modifier.align(Alignment.End),
                    )
                }

                // ── Image attachment ──────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.feedback_attach),
                            style = ty.bodySmall,
                            color = mc.textSecondary,
                        )
                        if (isCheckingAttachment) {
                            MagicLoadingSpinner(size = MagicLoadingSize.Small)
                        } else {
                            IconButton(onClick = { openImagePicker() }) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = stringResource(R.string.feedback_attach),
                                    tint = mc.primaryAccent,
                                )
                            }
                        }
                    }

                    val currentUri = attachedImageUri
                    if (currentUri != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(currentUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(ChipShape)
                                    .border(width = 1.dp, color = mc.surfaceVariant, shape = ChipShape),
                            )
                            IconButton(onClick = { attachedImageUri = null }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.action_remove),
                                    tint = mc.textSecondary,
                                )
                            }
                        }
                    }
                }

                // ── Send button ───────────────────────────────────────────────
                MagicCtaButton(
                    onClick = { sendFeedback() },
                    text = stringResource(R.string.feedback_send),
                    enabled = !isCheckingAttachment,
                    isLoading = isSending,
                    style = MagicCtaStyle.Filled,
                    color = MagicCtaColor.Primary,
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Toast overlay — positioned on top of all sheet content
            MagicToastHost(
                state = toastState,
                modifier = Modifier.matchParentSize(),
            )
        }
    }

    if (showDiscardDialog) {
        MagicAlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = stringResource(R.string.feedback_discard_title),
            text = stringResource(R.string.feedback_discard_text),
            confirmLabel = stringResource(R.string.action_discard),
            onConfirm = {
                showDiscardDialog = false
                resetAndDismiss()
            },
            dismissLabel = stringResource(R.string.action_cancel),
            onDismiss = { showDiscardDialog = false },
            confirmColor = MagicCtaColor.Error,
        )
    }
}
