package com.mmg.manahub.feature.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import com.mmg.manahub.core.domain.auth.AuthError
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.auth.AuthResult
import com.mmg.manahub.core.domain.auth.NicknameValidationResult
import com.mmg.manahub.core.domain.auth.NicknameValidator
import com.mmg.manahub.core.domain.auth.SessionState
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Backs the Profile edit sheet: nickname draft, planeswalker-art picker and the save/remove actions.
 *
 * Owned by the Profile back-stack entry, so the sheet calls [onSheetOpened] on every open to drop an
 * abandoned draft and to retry a failed first page. Saves run under [NonCancellable]: popping Profile
 * mid-save never leaves the local and remote values different.
 */
class ProfileEditViewModel(
    private val scryfallRemoteDataSource: ScryfallRemoteDataSource,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val authRepository: AuthRepository,
    private val crashReporter: CrashReporter,
) : ViewModel() {

    /** One artwork option in the avatar grid; [artCropUrl] is its unique key. */
    data class PlaneswalkerArt(
        val name: String,
        val artCropUrl: String,
        val colors: List<String>,
    )

    /** Why a save failed; the sheet maps each case to a message. */
    enum class SaveFailure { NICKNAME_INAPPROPRIATE, NICKNAME_TOO_LONG, GENERIC }

    /** One-shot outcomes for the sheet. */
    sealed interface Event {
        /** Every change was saved; the sheet closes. */
        data object Saved : Event

        /** A save failed; the sheet stays open with the draft intact. */
        data class SaveFailed(val reason: SaveFailure) : Event

        /** Removing the avatar failed; the current avatar is kept. */
        data object AvatarRemoveFailed : Event
    }

    /**
     * Sheet state.
     *
     * @property currentPage the last page successfully loaded (0 when none).
     * @property loadFailed the first page failed; the grid shows a retry.
     * @property appendFailed a later page failed; the grid footer shows a retry.
     */
    data class UiState(
        val artworks: List<PlaneswalkerArt> = emptyList(),
        val selectedColors: Set<String> = emptySet(),
        val isLoading: Boolean = false,
        val loadFailed: Boolean = false,
        val appendFailed: Boolean = false,
        val currentAvatarUrl: String? = null,
        val pendingSelection: String? = null,
        val hasMore: Boolean = false,
        val currentPage: Int = 0,
        val currentName: String = "",
        val pendingName: String = "",
        val gameTag: String? = null,
        val isSaving: Boolean = false,
    ) {
        /** Validation of the trimmed draft name. */
        val nameValidation: NicknameValidationResult get() = NicknameValidator.validate(pendingName)

        /** True when the trimmed draft differs from the saved name. */
        val isNameEdited: Boolean get() = NicknameValidator.normalize(pendingName) != currentName

        /** True when there is anything to save or discard. */
        val hasChanges: Boolean get() = isNameEdited || pendingSelection != null

        /** True when the draft can be saved right now. */
        val canSave: Boolean
            get() = hasChanges && !isSaving &&
                (!isNameEdited || nameValidation == NicknameValidationResult.VALID)
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

    private var loadJob: Job? = null

    // Main-thread only; a result from an older request is dropped even if its job outran cancel().
    private var loadGeneration = 0

    init {
        userPreferencesDataStore.playerNameFlow
            .distinctUntilChanged()
            .onEach { name ->
                _uiState.update { s ->
                    // Only follow the stored name while the user has not edited the field (P-10).
                    val untouched = s.pendingName == s.currentName
                    s.copy(currentName = name, pendingName = if (untouched) name else s.pendingName)
                }
            }
            .catch { reportFailure("profile_edit_name_flow_failed", it) }
            .launchIn(viewModelScope)

        authRepository.sessionState
            .onEach { session ->
                _uiState.update { it.copy(gameTag = (session as? SessionState.Authenticated)?.user?.gameTag) }
            }
            .catch { reportFailure("profile_edit_session_flow_failed", it) }
            .launchIn(viewModelScope)

        userPreferencesDataStore.avatarUrlFlow
            .onEach { url -> _uiState.update { it.copy(currentAvatarUrl = url) } }
            .catch { reportFailure("profile_edit_avatar_flow_failed", it) }
            .launchIn(viewModelScope)

        load(page = 1, append = false)
    }

    /** Resets an abandoned draft and retries a failed or missing first page (P-03, P-11). */
    fun onSheetOpened() {
        _uiState.update { it.copy(pendingSelection = null, pendingName = it.currentName) }
        val s = _uiState.value
        if (!s.isLoading && (s.artworks.isEmpty() || s.loadFailed)) load(page = 1, append = false)
    }

    /** Updates the draft name; input past [NicknameValidator.MAX_LENGTH] is ignored. */
    fun onNameChange(newName: String) {
        if (newName.length <= NicknameValidator.MAX_LENGTH) {
            _uiState.update { it.copy(pendingName = newName) }
        }
    }

    /** Toggles one colour filter and restarts the grid from page 1. */
    fun toggleColorFilter(color: String) {
        _uiState.update {
            val next = if (color in it.selectedColors) it.selectedColors - color else it.selectedColors + color
            it.copy(selectedColors = next)
        }
        restartGrid()
    }

    /** Clears every colour filter and restarts the grid from page 1. */
    fun clearColorFilters() {
        _uiState.update { it.copy(selectedColors = emptySet()) }
        restartGrid()
    }

    /** Loads the next page when more exist; a failed page waits for [retry] instead. */
    fun loadNextPage() {
        val s = _uiState.value
        if (!s.hasMore || s.isLoading || s.appendFailed || s.loadFailed) return
        load(page = s.currentPage + 1, append = true)
    }

    /** Retries the page that failed: page 1 when the grid is empty, otherwise the next page. */
    fun retry() {
        val s = _uiState.value
        if (s.isLoading) return
        if (s.artworks.isEmpty()) load(page = 1, append = false) else load(page = s.currentPage + 1, append = true)
    }

    /** Stages [artUrl] as the new avatar. */
    fun selectArt(artUrl: String) {
        _uiState.update { it.copy(pendingSelection = artUrl) }
    }

    /** Discards the draft name and art selection. */
    fun cancelSelection() {
        _uiState.update { it.copy(pendingSelection = null, pendingName = it.currentName) }
    }

    /**
     * Saves the draft. For a signed-in account the server write goes first and the local value is
     * written only after it succeeds (P-02, P-12); a guest saves locally only.
     */
    fun confirmChanges() {
        val s = _uiState.value
        if (!s.canSave) return
        val newName = if (s.isNameEdited) NicknameValidator.normalize(s.pendingName) else null
        val newAvatar = s.pendingSelection
        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val failure = withContext(NonCancellable) { persist(newName, newAvatar) }
            if (failure == null) {
                _uiState.update { it.copy(isSaving = false, pendingSelection = null) }
                _events.send(Event.Saved)
            } else {
                _uiState.update { it.copy(isSaving = false) }
                _events.send(Event.SaveFailed(failure))
            }
        }
    }

    /** Removes the avatar; for a signed-in account the removal reaches the server first. */
    fun removeAvatar() {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val removed = withContext(NonCancellable) {
                try {
                    if (isRealAccount()) {
                        val result = authRepository.updateAvatarUrl(null)
                        if (result is AuthResult.Error) {
                            reportAuthError("profile_avatar_remove_failed", result.error)
                            return@withContext false
                        }
                    }
                    userPreferencesDataStore.saveAvatarUrl(null)
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    reportFailure("profile_avatar_remove_failed", e)
                    false
                }
            }
            _uiState.update { it.copy(isSaving = false, pendingSelection = if (removed) null else it.pendingSelection) }
            if (!removed) _events.send(Event.AvatarRemoveFailed)
        }
    }

    private suspend fun persist(newName: String?, newAvatar: String?): SaveFailure? = try {
        val remote = isRealAccount()
        var failure: SaveFailure? = null
        if (newName != null) {
            val result = if (remote) authRepository.updateNickname(newName) else null
            if (result is AuthResult.Error) {
                reportAuthError("profile_nickname_update_failed", result.error)
                failure = when (result.error) {
                    AuthError.NicknameInappropriate -> SaveFailure.NICKNAME_INAPPROPRIATE
                    AuthError.NicknameTooLong -> SaveFailure.NICKNAME_TOO_LONG
                    else -> SaveFailure.GENERIC
                }
            } else {
                userPreferencesDataStore.savePlayerName(newName)
            }
        }
        if (failure == null && newAvatar != null) {
            val result = if (remote) authRepository.updateAvatarUrl(newAvatar) else null
            if (result is AuthResult.Error) {
                reportAuthError("profile_avatar_update_failed", result.error)
                failure = SaveFailure.GENERIC
            } else {
                userPreferencesDataStore.saveAvatarUrl(newAvatar)
            }
        }
        failure
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        reportFailure("profile_edit_save_failed", e)
        SaveFailure.GENERIC
    }

    private fun isRealAccount(): Boolean =
        (authRepository.sessionState.value as? SessionState.Authenticated)?.user?.isAnonymous == false

    private fun restartGrid() {
        _uiState.update {
            it.copy(artworks = emptyList(), currentPage = 0, hasMore = false, loadFailed = false, appendFailed = false)
        }
        load(page = 1, append = false)
    }

    // One load at a time: a new request cancels the previous one (P-04).
    private fun load(page: Int, append: Boolean) {
        loadJob?.cancel()
        val generation = ++loadGeneration
        val colors = _uiState.value.selectedColors
        _uiState.update { it.copy(isLoading = true, loadFailed = false, appendFailed = false) }

        loadJob = viewModelScope.launch {
            val loaded: Pair<List<PlaneswalkerArt>, Boolean> = try {
                val response = scryfallRemoteDataSource.searchPlaneswalkerArts(buildQuery(colors), page)
                val arts = response.data.mapNotNull { dto ->
                    val artUrl = dto.imageUris?.artCrop ?: dto.cardFaces?.firstOrNull()?.imageUris?.artCrop
                    artUrl?.let { PlaneswalkerArt(name = dto.name, artCropUrl = it, colors = dto.colors ?: emptyList()) }
                }
                arts to response.hasMore
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation != loadGeneration) return@launch
                if (e.isNotFound()) {
                    // Scryfall answers a search with zero results with 404.
                    _uiState.update { it.copy(isLoading = false, hasMore = false, currentPage = page) }
                } else {
                    reportFailure("profile_avatar_art_load_failed", e)
                    _uiState.update { it.copy(isLoading = false, loadFailed = !append, appendFailed = append) }
                }
                return@launch
            }
            if (generation != loadGeneration) return@launch
            val (arts, hasMore) = loaded
            _uiState.update { s ->
                val merged = if (append) s.artworks + arts else arts
                s.copy(
                    artworks = merged.distinctBy { it.artCropUrl },
                    isLoading = false,
                    hasMore = hasMore,
                    currentPage = page,
                )
            }
        }
    }

    private fun buildQuery(colors: Set<String>): String = buildString {
        append("t:planeswalker unique:art")
        val filter = colors.joinToString("") { it.lowercase() }
        if (filter.isNotEmpty()) append(" c:$filter")
    }

    private fun Throwable.isNotFound(): Boolean =
        this is ResponseException && response.status.value == HTTP_NOT_FOUND

    private fun reportAuthError(tag: String, error: AuthError) {
        val kind = error::class.simpleName ?: "unknown"
        crashReporter.setCustomKey("profile_auth_error", kind)
        crashReporter.log(tag)
        crashReporter.recordException(RuntimeException("[$tag] $kind"))
    }

    private fun reportFailure(tag: String, e: Throwable) {
        if (e is CancellationException) throw e
        crashReporter.log(tag)
        crashReporter.recordException(RuntimeException("[$tag] ${e::class.simpleName}"))
    }

    private companion object {
        const val HTTP_NOT_FOUND = 404
    }
}
