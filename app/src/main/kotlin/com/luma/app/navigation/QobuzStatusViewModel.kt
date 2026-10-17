package com.luma.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luma.core.data.discovery.HomeDiscoveryRepository
import com.luma.core.model.discovery.QobuzDiscoveryStatus
import com.luma.core.data.prefs.HomeDiscoveryPreference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class QobuzStatusViewModel @Inject constructor(
    homeDiscoveryRepository: HomeDiscoveryRepository,
    homeDiscoveryPreference: HomeDiscoveryPreference,
) : ViewModel() {
    val bannerStatus: StateFlow<QobuzDiscoveryStatus> = combine(
        homeDiscoveryRepository.status,
        homeDiscoveryPreference.enabled,
    ) { status, enabled ->
        if (enabled) status else QobuzDiscoveryStatus.OK
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QobuzDiscoveryStatus.OK)
}