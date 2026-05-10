package com.camryobd

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

class OBDService(
    private var ip: String = "192.168.0.10",
    private var port: Int = 35000,
    private var timeout: Int = 5000,
) {
    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    val isConnected: Boolean get() = socket?.isConnected == true

    suspend fun connect(address: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (address != null) {
                val parts = address.split(":")
                ip = parts[0]
                port = parts.getOrElse(1) { "35000" }.toInt()
            }

            socket?.close()
            socket = Socket().apply {
                connect(InetSocketAddress(ip, port), timeout)
                soTimeout = timeout
            }

            inputStream = socket?.getInputStream()
            outputStream = socket?.getOutputStream()

            Result.success(Unit)
        } catch (e: Exception) {
            socket?.close()
            socket = null
            Result.failure(e)
        }
    }

    suspend fun sendCommand(command: String): String = withContext(Dispatchers.IO) {
        try {
            val cmdBytes = (command + "\r\n").toByteArray()
            outputStream?.write(cmdBytes)
            outputStream?.flush()

            val buffer = java.lang.StringBuilder()
            val readBuffer = ByteArray(1024)
            var timeoutCount = 0

            while (timeoutCount < 100) {
                if (inputStream?.available() ?: 0 > 0) {
                    val bytes = inputStream?.read(readBuffer) ?: 0
                    buffer.append(String(readBuffer, 0, bytes))
                    val response = buffer.toString()

                    if (response.endsWith(">") || response.contains("OK") || response.contains("NO DATA")) {
                        break
                    }
                    timeoutCount = 0
                } else {
                    kotlinx.coroutines.delay(50)
                    timeoutCount++
                }
            }

            buffer.toString().trim()
        } catch (e: Exception) {
            throw e
        }
    }

    fun disconnect() {
        try {
            inputStream?.close()
            outputStream?.close()
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        inputStream = null
        outputStream = null
    }
}
