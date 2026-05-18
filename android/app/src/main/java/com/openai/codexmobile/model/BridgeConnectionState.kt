package com.openai.codexmobile.model

sealed interface BridgeConnectionState {
    data object Disconnected : BridgeConnectionState
    data class Connected(
        val endpoint: String,
        val service: String? = null,
        val runnerMode: String? = null,
        val transport: String = "HTTP",
        val provider: String = "bridge",
    ) : BridgeConnectionState
}
