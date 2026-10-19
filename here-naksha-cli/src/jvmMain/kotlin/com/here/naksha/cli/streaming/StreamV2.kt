package com.here.naksha.cli.streaming

import naksha.base.NakshaError
import naksha.base.Version
import naksha.model.IStreamSession
import naksha.model.streaming.Stream
import naksha.model.streaming.StreamChunk
import naksha.model.streaming.StreamRequest

class StreamV2(session: IStreamSession, request: StreamRequest) : Stream(session, request) {
    override val minVersion: Version
        get() = Version.fromDouble(request.minVersion.toDouble())
    override val unacknowledgedChunks: Array<StreamChunk>
        get() = _chunks.subtract(_acknowledgedChunksList.toSet()).toTypedArray()
    override val acknowledgedChunks: Array<StreamChunk>
        get() = _acknowledgedChunksList.toTypedArray()
    override val estimatedChunks: Long = -1
    override val estimatedTuples: Long = -1
    override val acknowledgedTuples: Long = 0
    override val unacknowledgedTuples: Long = 0
    override val isRecoverable: Boolean = false

    private val _chunks = ArrayList<StreamChunk>()
    private val _acknowledgedChunksList = ArrayList<StreamChunk>()

    internal fun addChunk(chunk: StreamChunk) {
        _chunks.add(chunk)
    }

    override fun hasNext(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isAcknowledged(timeoutMillis: Long): Boolean {
        return _chunks == _acknowledgedChunksList
    }

    override fun next(): StreamChunk {
        TODO("Not yet implemented")
    }

    override fun next(timeoutMillis: Long): StreamChunk? {
        TODO("Not yet implemented")
    }

    override fun acknowledge(chunk: StreamChunk) {
        _acknowledgedChunksList.add(chunk)
    }

    override fun fail(chunk: StreamChunk, reason: NakshaError) {
        TODO("Not yet implemented")
    }

    override fun isClosed(): Boolean {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    override fun closeForRecovery(): StreamRequest {
        TODO("Not yet implemented")
    }

    override fun getRecoveryRequest(timeoutMillis: Long): StreamRequest {
        TODO("Not yet implemented")
    }
}