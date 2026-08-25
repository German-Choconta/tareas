package com.germanchoconta.gymtracker.ui.recovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.germanchoconta.gymtracker.data.health.RecoveryAvailability
import com.germanchoconta.gymtracker.data.health.RecoveryContext
import com.germanchoconta.gymtracker.data.health.RecoveryContextNormalizer
import com.germanchoconta.gymtracker.data.health.RecoveryHealthSource
import com.germanchoconta.gymtracker.data.health.RecoveryPermission
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

data class RecoveryUiState(
    val availability: RecoveryAvailability? = null,
    val loading: Boolean = false,
    val grantedPermissions: Set<RecoveryPermission> = emptySet(),
    val context: RecoveryContext? = null,
    val permissionChanged: Boolean = false,
    val error: Boolean = false,
) {
    val allPermissionsGranted: Boolean
        get() = grantedPermissions.containsAll(RecoveryPermission.entries)

    val hasAnyPermission: Boolean
        get() = grantedPermissions.isNotEmpty()
}

class RecoveryContextViewModel internal constructor(
    private val source: RecoveryHealthSource,
    private val today: () -> LocalDate = LocalDate::now,
    private val zoneId: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {
    val requestedPermissionStrings: Set<String>
        get() = source.requestedPermissionStrings

    private val _uiState = MutableStateFlow(RecoveryUiState())
    val uiState: StateFlow<RecoveryUiState> = _uiState.asStateFlow()
    private val refreshMutex = Mutex()

    fun refresh() {
        val availability = source.availability()
        if (availability != RecoveryAvailability.AVAILABLE) {
            _uiState.value = RecoveryUiState(availability = availability)
            return
        }
        if (!refreshMutex.tryLock()) return

        _uiState.update {
            it.copy(
                availability = availability,
                loading = true,
                error = false,
                permissionChanged = false,
            )
        }
        viewModelScope.launch {
            try {
                val granted = source.grantedPermissions()
                if (granted.isEmpty()) {
                    _uiState.value = RecoveryUiState(
                        availability = RecoveryAvailability.AVAILABLE,
                        grantedPermissions = emptySet(),
                    )
                    return@launch
                }

                val currentDay = today()
                val currentZone = zoneId()
                val raw = source.readRawContext(
                    day = currentDay,
                    zoneId = currentZone,
                    grantedPermissions = granted,
                )
                _uiState.value = RecoveryUiState(
                    availability = RecoveryAvailability.AVAILABLE,
                    grantedPermissions = granted,
                    context = RecoveryContextNormalizer.normalize(raw, currentDay, currentZone),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: SecurityException) {
                val grantedAfterChange = runCatching { source.grantedPermissions() }.getOrDefault(emptySet())
                _uiState.value = RecoveryUiState(
                    availability = RecoveryAvailability.AVAILABLE,
                    grantedPermissions = grantedAfterChange,
                    permissionChanged = true,
                )
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        context = null,
                        error = true,
                    )
                }
            } finally {
                _uiState.update { it.copy(loading = false) }
                refreshMutex.unlock()
            }
        }
    }

    fun onPermissionResult() {
        refresh()
    }

    fun disconnect() {
        if (!refreshMutex.tryLock()) return
        _uiState.update { it.copy(loading = true, error = false) }
        viewModelScope.launch {
            try {
                source.disconnect()
                _uiState.value = RecoveryUiState(availability = source.availability())
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update { it.copy(loading = false, error = true) }
            } finally {
                _uiState.update { it.copy(loading = false) }
                refreshMutex.unlock()
            }
        }
    }

    companion object {
        fun factory(source: RecoveryHealthSource): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    RecoveryContextViewModel(source) as T
            }
    }
}
