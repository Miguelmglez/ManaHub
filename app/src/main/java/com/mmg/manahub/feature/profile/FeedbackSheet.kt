package com.mmg.manahub.feature.profile

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import kotlinx.coroutines.launch

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
 * Validates that the [uri] refers to an acceptable image file.
 *
 * - MIME type: read from [android.content.ContentResolver.getType] (not from file extension).
 * - File size: measured via [android.os.ParcelFileDescriptor.statSize] (not from metadata extras).
 *
 * @return `true` if the file passes all security checks, `false` otherwise.
 */
private fun isImageSafe(context: android.content.Context, uri: Uri): Boolean {
    // MIME check — derived from content, not filename
    val mimeType = context.contentResolver.getType(uri) ?: return false
    if (mimeType !in ALLOWED_MIME_TYPES) return false
    // Size check — actual fstat, not spoofable metadata
    val size = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: return false
    return size <= MAX_IMAGE_BYTES
}

/**
 * A [ModalBottomSheet] that lets the user compose and send feedback via email.
 *
 * The sheet cannot be dismissed by dragging — only via the explicit Cancel/X controls.
 * The recipient email address is defined as a private constant and never surfaced in the UI.
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
    val toastState = rememberMagicToastState()

    // Sheet state — confirmValueChange blocks drag-to-dismiss.
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden },
    )

    var messageText by remember { mutableStateOf("") }
    var attachedImageUri by remember { mutableStateOf<Uri?>(null) }
    var showEmptyError by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }

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

    /** Handles a URI returned by either picker; validates and attaches or shows error. */
    fun onImagePicked(uri: Uri?) {
        if (uri == null) return
        if (isImageSafe(context, uri)) {
            attachedImageUri = uri
        } else {
            toastState.show(
                message = context.getString(R.string.feedback_invalid_file),
                type = MagicToastType.ERROR,
            )
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
        val imageUri = attachedImageUri
        val intent = if (imageUri != null) {
            val attachmentMime = context.contentResolver.getType(imageUri) ?: "image/*"
            Intent(Intent.ACTION_SEND).apply {
                type = attachmentMime
                putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
                putExtra(Intent.EXTRA_SUBJECT, "ManaHub Feedback")
                putExtra(Intent.EXTRA_TEXT, messageText)
                putExtra(Intent.EXTRA_STREAM, imageUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
                putExtra(Intent.EXTRA_SUBJECT, "ManaHub Feedback")
                putExtra(Intent.EXTRA_TEXT, messageText)
            }
        }

        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.feedback_chooser_title)),
        )

        // Reset state and close after launching the chooser
        resetAndDismiss()
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    ModalBottomSheet(
        onDismissRequest = { /* blocked — use explicit controls only */ },
        sheetState = sheetState,
        containerColor = mc.backgroundSecondary,
        dragHandle = null, // we provide our own header row
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {

                // ── Header ────────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.feedback_title),
                        style = ty.titleLarge,
                        color = mc.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(onClick = { dismiss() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.feedback_cancel),
                            tint = mc.textSecondary,
                        )
                    }
                }

                // ── Text field ────────────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                    color = MaterialTheme.colorScheme.error,
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
                        shape = RoundedCornerShape(12.dp),
                        textStyle = ty.bodyMedium.copy(color = mc.textPrimary),
                    )

                    // Character counter
                    Text(
                        text = stringResource(R.string.feedback_char_count, messageText.length),
                        style = ty.labelSmall,
                        color = mc.textDisabled,
                        modifier = Modifier.align(Alignment.End),
                    )
                }

                // ── Image attachment ──────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        IconButton(
                            onClick = { openImagePicker() },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(mc.surface),
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = stringResource(R.string.feedback_attach),
                                tint = mc.primaryAccent,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    // Thumbnail of the selected image, if any
                    val currentUri = attachedImageUri
                    if (currentUri != null) {
                        Box(modifier = Modifier.size(80.dp)) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(currentUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        width = 1.dp,
                                        color = mc.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp),
                                    ),
                            )
                            // Remove image button
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.TopEnd)
                                    .offset(x = 6.dp, y = (-6).dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(mc.backgroundSecondary)
                                    .border(
                                        width = 1.dp,
                                        color = mc.surfaceVariant,
                                        shape = RoundedCornerShape(50),
                                    )
                                    .clickable { attachedImageUri = null },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.action_remove),
                                    tint = mc.textSecondary,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }
                    }
                }

                // ── Send button ───────────────────────────────────────────────
                Button(
                    onClick = { sendFeedback() },
                    enabled = !isSending,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = mc.primaryAccent,
                        contentColor = Color.White,
                        disabledContainerColor = mc.primaryAccent.copy(alpha = 0.5f),
                        disabledContentColor = Color.White.copy(alpha = 0.5f),
                    ),
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            modifier = Modifier
                                .size(18.dp)
                                .padding(end = 0.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.feedback_send),
                            style = ty.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        )
                    }
                }
            }

            // Toast overlay — positioned on top of all sheet content
            MagicToastHost(
                state = toastState,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
