package com.offlinebot.recorder.service

interface RecorderController {
    suspend fun start()
    suspend fun pause()
    suspend fun resume()
    suspend fun stop()
}

class RecorderPermissionException(permission: String) :
    IllegalStateException("Missing required permission: $permission")
