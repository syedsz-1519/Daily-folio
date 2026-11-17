package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.security.VaultPreferencesRepository
import com.example.data.security.VaultSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VaultViewModel(
    private val preferencesRepository: VaultPreferencesRepository
) : ViewModel() {

    val vaultSettings: StateFlow<VaultSettings> = preferencesRepository.vaultSettings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = VaultSettings()
        )

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _pinError = MutableStateFlow<String?>(null)
    val pinError: StateFlow<String?> = _pinError.asStateFlow()

    private var initialCheckDone = false

    init {
        viewModelScope.launch {
            val initial = preferencesRepository.vaultSettings.first()
            if (!initialCheckDone) {
                initialCheckDone = true
                _isLocked.value = initial.vaultLockEnabled
            }
        }
    }

    fun onAppBackgrounded() {
        val current = vaultSettings.value
        if (current.vaultLockEnabled && current.lockOnBackground) {
            _isLocked.value = true
        }
    }

    fun lockNow() {
        if (vaultSettings.value.hasPinConfigured) {
            viewModelScope.launch {
                preferencesRepository.setVaultLockEnabled(true)
                _isLocked.value = true
            }
        }
    }

    fun unlockWithPin(pin: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val valid = preferencesRepository.verifyPin(pin)
            if (valid) {
                _pinError.value = null
                _isLocked.value = false
                onSuccess()
            } else {
                _pinError.value = "Incorrect PIN. Please try again."
            }
        }
    }

    fun unlockViaBiometric() {
        _pinError.value = null
        _isLocked.value = false
    }

    fun configurePin(pin: String, enableBiometric: Boolean, onDone: () -> Unit) {
        viewModelScope.launch {
            preferencesRepository.setPin(pin, enableBiometric)
            _pinError.value = null
            _isLocked.value = false
            onDone()
        }
    }

    fun setVaultLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setVaultLockEnabled(enabled)
            if (!enabled) {
                _isLocked.value = false
            }
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setBiometricEnabled(enabled)
        }
    }

    fun setLockOnBackground(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setLockOnBackground(enabled)
        }
    }

    fun removePin() {
        viewModelScope.launch {
            preferencesRepository.clearPin()
            _isLocked.value = false
            _pinError.value = null
        }
    }

    fun clearPinError() {
        _pinError.value = null
    }
}
