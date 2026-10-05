package me.mudkip.moememos.ui.page.memoinput

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.skydoves.sandwich.suspendOnSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.mudkip.moememos.MainActivity
import me.mudkip.moememos.MoeMemosFileProvider
import me.mudkip.moememos.R
import me.mudkip.moememos.data.model.MemoVisibility
import me.mudkip.moememos.data.model.ShareContent
import me.mudkip.moememos.ext.popBackStackIfLifecycleIsResumed
import me.mudkip.moememos.ext.string
import me.mudkip.moememos.ext.suspendOnErrorMessage
import me.mudkip.moememos.ui.page.common.LocalRootNavController
import me.mudkip.moememos.util.extractCustomTags
import me.mudkip.moememos.viewmodel.LocalMemos
import me.mudkip.moememos.viewmodel.LocalUserState
import me.mudkip.moememos.viewmodel.MemoInputViewModel
import java.io.File
import java.util.Locale

private const val MaxSelectableMedia = 100

@Composable
fun MemoInputPage(
    viewModel: MemoInputViewModel = hiltViewModel(),
    memoIdentifier: String? = null,
    shareContent: ShareContent? = null,
    quickCaptureMode: String? = null,
    onQuickCaptureConsumed: () -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    val snackbarState = remember { SnackbarHostState() }
    val navController = LocalRootNavController.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val memosViewModel = LocalMemos.current
    val userStateViewModel = LocalUserState.current
    val currentAccount by userStateViewModel.currentAccount.collectAsStateWithLifecycle()
    val memo = remember { memosViewModel.memos.toList().find { it.identifier == memoIdentifier } }
    // What the editor was loaded from: `memo`, replaced by the database row once it is read (the list
    // copy above is not refreshed while a push or sync runs, so it can be older).
    var baseline by remember { mutableStateOf(memo) }
    val autosaveEnabled by viewModel.autosaveEnabled.collectAsStateWithLifecycle(initialValue = false)
    var autosaveIdentifier by rememberSaveable { mutableStateOf(memo?.identifier) }
    var autosaveDirty by remember { mutableStateOf(false) }
    var exiting by remember { mutableStateOf(false) }
    var initialContent by remember { mutableStateOf(memo?.content ?: "") }
    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(memo?.content ?: "", TextRange(memo?.content?.length ?: 0)))
    }
    var visibilityMenuExpanded by remember { mutableStateOf(false) }
    var tagMenuExpanded by remember { mutableStateOf(false) }
    var photoImageUri by remember { mutableStateOf<Uri?>(null) }
    var showExitConfirmation by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var pendingUploadJobs by remember { mutableStateOf(0) }
    val audioRecorder = remember {
        MemoAudioRecorder(context.applicationContext) { completedRecording ->
            Handler(Looper.getMainLooper()).post {
                isRecording = false
                if (completedRecording == null) {
                    coroutineScope.launch {
                        snackbarState.showSnackbar(R.string.recording_failed.string)
                    }
                    return@post
                }
                pendingUploadJobs += 1
                coroutineScope.launch {
                    try {
                        val uri = MoeMemosFileProvider.getFileUri(context, completedRecording)
                        viewModel.upload(uri, autosaveIdentifier ?: memo?.identifier).suspendOnErrorMessage { message ->
                            snackbarState.showSnackbar(message)
                        }
                    } finally {
                        completedRecording.delete()
                        pendingUploadJobs = (pendingUploadJobs - 1).coerceAtLeast(0)
                    }
                }
            }
        }
    }

    val defaultVisibility = userStateViewModel.currentUser?.defaultVisibility ?: MemoVisibility.PRIVATE
    var currentVisibility by remember { mutableStateOf(memo?.visibility ?: defaultVisibility) }

    val validMimeTypePrefixes = remember {
        setOf("text/")
    }

    // An existing memo nothing was changed in. Autosave must not write it back on Send/Back: if the
    // text is older than the database (see `baseline`), writing it would revert newer changes.
    fun isUntouchedExistingMemo(): Boolean {
        val base = baseline ?: return false
        return !autosaveDirty &&
            text.text == base.content &&
            currentVisibility == base.visibility &&
            viewModel.uploadResources.size == base.resources.size
    }

    fun submit() = coroutineScope.launch {
        if (isRecording || pendingUploadJobs > 0) {
            return@launch
        }
        val tags = extractCustomTags(text.text)

        if (autosaveEnabled && isUntouchedExistingMemo()) {
            exiting = true
            navController.popBackStack()
            return@launch
        }

        if (autosaveEnabled) {
            viewModel.flushAutosave(text.text, currentVisibility, tags.toList(), clearDraftOnCreate = shareContent == null).suspendOnSuccess {
                exiting = true
                memosViewModel.refreshLocalSnapshot()
                navController.popBackStack()
            }.suspendOnErrorMessage { message ->
                snackbarState.showSnackbar(message)
            }
            return@launch
        }

        memo?.let {
            viewModel.editMemo(memo.identifier, text.text, currentVisibility, tags.toList()).suspendOnSuccess {
                memosViewModel.refreshLocalSnapshot()
                navController.popBackStack()
            }.suspendOnErrorMessage { message ->
                snackbarState.showSnackbar(message)
            }
            return@launch
        }

        viewModel.createMemo(text.text, currentVisibility, tags.toList()).suspendOnSuccess {
            text = TextFieldValue("")
            viewModel.updateDraft("")
            memosViewModel.refreshLocalSnapshot()
            navController.popBackStack()
        }.suspendOnErrorMessage { message ->
            snackbarState.showSnackbar(message)
        }
    }

    fun handleExit() {
        if (isRecording || pendingUploadJobs > 0) {
            return
        }
        if (autosaveEnabled) {
            coroutineScope.launch {
                // Only a row this editor created may be discarded. `memo` is also null when editing a
                // memo the list has not loaded (e.g. after process death); that memo must not be deleted.
                val autosaveRow = viewModel.autosaveIdentifier
                val ownsAutosaveRow = autosaveRow == null || autosaveRow != memoIdentifier
                if (ownsAutosaveRow && text.text.isEmpty() && viewModel.uploadResources.isEmpty()) {
                    viewModel.discardEmptyAutosave()
                } else if (!isUntouchedExistingMemo()) {
                    viewModel.flushAutosave(
                        text.text,
                        currentVisibility,
                        extractCustomTags(text.text).toList(),
                        clearDraftOnCreate = shareContent == null
                    )
                }
                exiting = true
                memosViewModel.refreshLocalSnapshot()
                navController.popBackStackIfLifecycleIsResumed(lifecycleOwner)
            }
            return
        }
        if (text.text != initialContent || viewModel.uploadResources.size != (baseline?.resources?.size ?: 0)) {
            showExitConfirmation = true
        } else {
            navController.popBackStackIfLifecycleIsResumed(lifecycleOwner)
        }
    }

    fun uploadUris(uris: List<Uri>) {
        val selectedUris = uris.take(MaxSelectableMedia)
        if (selectedUris.isEmpty()) {
            return
        }
        // Increment before launching so Submit/Back cannot race the first suspension in upload().
        pendingUploadJobs += 1
        coroutineScope.launch {
            try {
                selectedUris.forEach { uri ->
                    viewModel.upload(uri, autosaveIdentifier ?: memo?.identifier).suspendOnErrorMessage { message ->
                        snackbarState.showSnackbar(message)
                    }
                }
                delay(300)
                focusRequester.requestFocus()
            } finally {
                pendingUploadJobs = (pendingUploadJobs - 1).coerceAtLeast(0)
            }
        }
    }

    fun uploadUri(uri: Uri) {
        uploadUris(listOf(uri))
    }

    val pickMedia = rememberLauncherForActivityResult(
        PickMultipleVisualMedia(MaxSelectableMedia)
    ) { uris ->
        if (uris.isNotEmpty()) {
            uploadUris(uris)
        }
    }

    val takePhoto = rememberLauncherForActivityResult(TakePicture()) { success ->
        if (success) {
            photoImageUri?.let { uploadUri(it) }
        }
    }

    val pickAttachments = rememberLauncherForActivityResult(OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            uploadUris(uris)
        }
    }

    val speechInput = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.takeIf(String::isNotBlank)
                ?.let { recognized -> text = insertSpeechResult(text, recognized) }
        }
    }

    fun launchSpeechInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, R.string.voice_input.string)
        }
        try {
            speechInput.launch(intent)
        } catch (_: ActivityNotFoundException) {
            coroutineScope.launch {
                snackbarState.showSnackbar(R.string.speech_recognition_unavailable.string)
            }
        }
    }

    fun startRecording() {
        try {
            audioRecorder.start()
            isRecording = true
        } catch (_: Throwable) {
            audioRecorder.cancel()
            isRecording = false
            coroutineScope.launch {
                snackbarState.showSnackbar(R.string.recording_failed.string)
            }
        }
    }

    val requestAudioPermission = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        if (granted) {
            startRecording()
        } else {
            coroutineScope.launch {
                snackbarState.showSnackbar(R.string.microphone_permission_required.string)
            }
        }
    }

    fun finishRecording() {
        val recording = audioRecorder.stop()
        isRecording = false
        if (recording == null) {
            coroutineScope.launch {
                snackbarState.showSnackbar(R.string.recording_too_short.string)
            }
            return
        }
        pendingUploadJobs += 1
        coroutineScope.launch {
            try {
                val uri = MoeMemosFileProvider.getFileUri(context, recording)
                viewModel.upload(uri, autosaveIdentifier ?: memo?.identifier).suspendOnErrorMessage { message ->
                    snackbarState.showSnackbar(message)
                }
            } finally {
                recording.delete()
                pendingUploadJobs = (pendingUploadJobs - 1).coerceAtLeast(0)
            }
        }
    }

    fun toggleRecording() {
        if (!isRecording) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startRecording()
            } else {
                requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
            return
        }

        finishRecording()
    }

    fun cancelRecording() {
        audioRecorder.cancel()
        isRecording = false
    }

    fun launchCamera() {
        try {
            val uri = MoeMemosFileProvider.getImageUri(context)
            photoImageUri = uri
            takePhoto.launch(uri)
        } catch (e: ActivityNotFoundException) {
            coroutineScope.launch {
                snackbarState.showSnackbar(e.localizedMessage ?: R.string.camera_unavailable.string)
            }
        }
    }

    BackHandler {
        handleExit()
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            MemoInputTopBar(
                isEditMode = memo != null,
                canSubmit = !isRecording &&
                    pendingUploadJobs == 0 &&
                    (text.text.isNotEmpty() || viewModel.uploadResources.isNotEmpty()),
                onClose = { handleExit() },
                onSubmit = { submit() }
            )
        },
        bottomBar = {
            MemoInputBottomBar(
                currentAccount = currentAccount,
                currentVisibility = currentVisibility,
                showSpaceVisibility = memo?.visibility == MemoVisibility.SPACE,
                visibilityMenuExpanded = visibilityMenuExpanded,
                onVisibilityExpandedChange = { visibilityMenuExpanded = it },
                onVisibilitySelected = { currentVisibility = it },
                tags = memosViewModel.tags.toList(),
                tagMenuExpanded = tagMenuExpanded,
                onTagExpandedChange = { tagMenuExpanded = it },
                onHashTagClick = {
                    text = replaceSelection(text, "#")
                },
                onTagSelected = { tag ->
                    text = replaceSelection(text, "#$tag ")
                },
                onToggleTodoItem = {
                    text = toggleTodoItemInText(text)
                },
                isRecording = isRecording,
                onVoiceInput = { launchSpeechInput() },
                onToggleRecording = { toggleRecording() },
                onCancelRecording = { cancelRecording() },
                onPickMedia = {
                    pickMedia.launch(PickVisualMediaRequest(PickVisualMedia.ImageAndVideo))
                },
                onPickAttachment = {
                    pickAttachments.launch(arrayOf("*/*"))
                },
                onTakePhoto = {
                    launchCamera()
                },
                onFormat = { format ->
                    text = applyMarkdownFormatToText(text, format)
                }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarState)
        }
    ) { innerPadding ->
        MemoInputEditor(
            modifier = Modifier.padding(innerPadding),
            text = text,
            onTextChange = { updated ->
                if (
                    text.text != updated.text &&
                    updated.selection.start == updated.selection.end &&
                    updated.text.length == text.text.length + 1 &&
                    updated.selection.start > 0 &&
                    updated.text[updated.selection.start - 1] == '\n'
                ) {
                    val handled = handleEnterInText(text)
                    if (handled != null) {
                        text = handled
                        return@MemoInputEditor
                    }
                }
                text = updated
            },
            focusRequester = focusRequester,
            validMimeTypePrefixes = validMimeTypePrefixes,
            onDroppedText = { droppedText ->
                text = text.copy(text = text.text + droppedText)
            },
            uploadResources = viewModel.uploadResources.toList(),
            inputViewModel = viewModel
        )
    }

    if (showExitConfirmation) {
        SaveChangesDialog(
            onSave = {
                showExitConfirmation = false
                submit()
            },
            onDiscard = {
                showExitConfirmation = false
                text = TextFieldValue("")
                navController.popBackStackIfLifecycleIsResumed(lifecycleOwner)
            },
            onDismiss = {
                showExitConfirmation = false
            }
        )
    }

    LaunchedEffect(Unit) {
        viewModel.autosaveIdentifier = autosaveIdentifier
        viewModel.uploadResources.clear()
        when {
            memo != null -> {
                viewModel.uploadResources.addAll(memo.resources)
                initialContent = memo.content
                // Adopt the database row if the list copy was stale and nothing was edited yet
                val fresh = viewModel.loadMemo(memo.identifier)
                if (fresh != null && fresh != memo && isUntouchedExistingMemo()) {
                    baseline = fresh
                    initialContent = fresh.content
                    currentVisibility = fresh.visibility
                    viewModel.uploadResources.clear()
                    viewModel.uploadResources.addAll(fresh.resources)
                    text = TextFieldValue(fresh.content, TextRange(fresh.content.length))
                }
            }

            shareContent != null -> {
                text = TextFieldValue(shareContent.text, TextRange(shareContent.text.length))
                for (item in shareContent.attachments) {
                    uploadUri(item)
                }
            }

            else -> {
                // After process death with an autosaved row, the restored text is newer than the draft
                if (autosaveIdentifier == null) {
                    viewModel.draft.first()?.let {
                        text = TextFieldValue(it, TextRange(it.length))
                    }
                }
            }
        }
        delay(300)
        focusRequester.requestFocus()
    }

    LaunchedEffect(quickCaptureMode) {
        when (quickCaptureMode) {
            MainActivity.CAPTURE_MODE_VOICE -> launchSpeechInput()
            MainActivity.CAPTURE_MODE_CAMERA -> launchCamera()
            MainActivity.CAPTURE_MODE_MEDIA -> {
                pickMedia.launch(PickVisualMediaRequest(PickVisualMedia.ImageAndVideo))
            }
        }
        if (quickCaptureMode != null) {
            onQuickCaptureConsumed()
        }
    }

    // Restarts per change; a restart cancels a previous run still waiting for the autosave mutex,
    // so at most one write runs and one (the newest) waits.
    LaunchedEffect(autosaveEnabled, text.text, currentVisibility, viewModel.uploadResources.size) {
        if (!autosaveEnabled) {
            return@LaunchedEffect
        }
        autosaveIdentifier = autosaveIdentifier ?: viewModel.autosaveIdentifier
        if (autosaveIdentifier == null && text.text.isEmpty() && viewModel.uploadResources.isEmpty()) {
            return@LaunchedEffect
        }
        if (isUntouchedExistingMemo()) {
            return@LaunchedEffect
        }
        autosaveDirty = true
        viewModel.autosave(
            text.text,
            currentVisibility,
            extractCustomTags(text.text).toList(),
            clearDraftOnCreate = shareContent == null
        ).suspendOnSuccess {
            autosaveIdentifier = data.identifier
        }
    }

    // Leaving the app: rewrite the latest text and push it now. The deferred push only lives as long
    // as the process, and the app lock tears this page down on return without calling handleExit.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && isRecording) {
                // Do not leave the microphone active in the background. Finalize the current clip
                // and enqueue it before the activity can be reclaimed.
                finishRecording()
            }
            if (event == Lifecycle.Event.ON_STOP && autosaveEnabled && autosaveDirty && !exiting) {
                viewModel.flushAutosaveInBackground(
                    text.text,
                    currentVisibility,
                    extractCustomTags(text.text).toList(),
                    clearDraftOnCreate = shareContent == null
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (autosaveEnabled) {
                // Already flushed by submit/handleExit; a system-initiated dispose leaves needsSync
                // set, so the deferred push or the next sync uploads the row.
                return@onDispose
            }
            if (memo == null && shareContent == null) {
                viewModel.updateDraft(text.text)
            }
        }
    }

    DisposableEffect(audioRecorder) {
        onDispose {
            audioRecorder.cancel()
        }
    }
}
