package com.example.unimate.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NetworkState {
    ONLINE,
    OFFLINE,
}

interface NetworkMonitor {
    val networkState: StateFlow<NetworkState>
    val isOnline: Boolean
        get() = networkState.value == NetworkState.ONLINE
}

class MutableNetworkMonitor(
    initialState: NetworkState = NetworkState.ONLINE,
) : NetworkMonitor {
    private val _networkState = MutableStateFlow(initialState)
    override val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

    fun setState(state: NetworkState) {
        _networkState.value = state
    }

    fun setOnline(online: Boolean) {
        _networkState.value = if (online) NetworkState.ONLINE else NetworkState.OFFLINE
    }
}

class AndroidConnectivityNetworkMonitor(
    context: Context,
) : NetworkMonitor {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _networkState = MutableStateFlow(computeCurrentState())
    override val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            refreshState()
        }

        override fun onLost(network: Network) {
            refreshState()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) {
            val hasInternet =
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated =
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            if (hasInternet && isValidated) {
                _networkState.value = NetworkState.ONLINE
            } else {
                refreshState()
            }
        }

        override fun onUnavailable() {
            refreshState()
        }
    }

    init {
        registerCallback()
    }

    private fun registerCallback() {
        val cm = connectivityManager ?: return
        runCatching {
            cm.registerDefaultNetworkCallback(networkCallback)
        }.onFailure {
            runCatching {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                cm.registerNetworkCallback(request, networkCallback)
            }
        }
    }

    private fun refreshState() {
        _networkState.value = computeCurrentState()
    }

    private fun computeCurrentState(): NetworkState {
        val cm = connectivityManager ?: return NetworkState.ONLINE
        val activeNetwork = runCatching { cm.activeNetwork }.getOrNull() ?: return NetworkState.OFFLINE
        val capabilities = runCatching { cm.getNetworkCapabilities(activeNetwork) }.getOrNull()
            ?: return NetworkState.OFFLINE
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return if (hasInternet && isValidated) NetworkState.ONLINE else NetworkState.OFFLINE
    }
}
