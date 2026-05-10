package com.camryobd

import android.content.Context
import android.util.Log
import com.camryobd.models.BatteryPackData
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DataLogger(private val context: Context) {
    private var currentFile: File? = null
    private var fileWriter: FileWriter? = null
    private var isLogging = false

    fun startLogging(): Boolean {
        if (isLogging) return true
        
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "BatteryLog_$timeStamp.csv"
            
            // Save to internal app directory, easily accessible via Intent later
            currentFile = File(context.getExternalFilesDir(null), fileName)
            fileWriter = FileWriter(currentFile, true)
            
            // Write CSV header
            val header = "Timestamp,MaxV,MinV,Delta_mV,AvgV," + (1..14).joinToString(",") { "Block$it" } + "\n"
            fileWriter?.append(header)
            fileWriter?.flush()
            
            isLogging = true
            return true
        } catch (e: Exception) {
            Log.e("DataLogger", "Failed to start logging: ${e.message}")
            return false
        }
    }

    fun logData(pack: BatteryPackData) {
        if (!isLogging || fileWriter == null || pack.blocks.isEmpty()) return

        try {
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val sb = StringBuilder()
            
            sb.append(time).append(",")
            sb.append(String.format(Locale.US, "%.3f", pack.maxVoltage)).append(",")
            sb.append(String.format(Locale.US, "%.3f", pack.minVoltage)).append(",")
            sb.append(String.format(Locale.US, "%.0f", pack.deltaMv)).append(",")
            sb.append(String.format(Locale.US, "%.3f", pack.avgVoltage)).append(",")
            
            // Add block voltages
            for (i in 0 until 14) {
                if (i < pack.blocks.size) {
                    sb.append(String.format(Locale.US, "%.3f", pack.blocks[i].voltage))
                }
                sb.append(if (i == 13) "" else ",")
            }
            sb.append("\n")
            
            fileWriter?.append(sb.toString())
            fileWriter?.flush()
        } catch (e: Exception) {
            Log.e("DataLogger", "Failed to log data: ${e.message}")
        }
    }

    fun stopLogging(): File? {
        if (!isLogging) return currentFile
        
        try {
            fileWriter?.flush()
            fileWriter?.close()
        } catch (e: Exception) {
            Log.e("DataLogger", "Failed to close file: ${e.message}")
        } finally {
            fileWriter = null
            isLogging = false
        }
        return currentFile
    }

    fun isCurrentlyLogging(): Boolean = isLogging
}
