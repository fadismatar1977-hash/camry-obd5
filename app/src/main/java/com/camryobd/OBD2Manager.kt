package com.camryobd

import com.camryobd.models.BatteryPackData
import com.camryobd.models.BlockVoltage
import com.camryobd.models.DiagnosticTroubleCode
import com.camryobd.models.HybridData
import com.camryobd.models.SensorData

class OBD2Manager(private val obd: OBDService) {

    suspend fun initialize(): Result<Unit> {
        return try {
            obd.sendCommand("ATZ")
            obd.sendCommand("ATE0")
            obd.sendCommand("ATL0")
            obd.sendCommand("ATS0")
            obd.sendCommand("ATH0")
            obd.sendCommand("ATSP0")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getEngineRpm(): Result<SensorData> {
        return try {
            val raw = sendOBDCommand("010C")
            val value = parseHexResponse(raw)
            if (value >= 0) {
                Result.success(SensorData("RPM", (value / 4).toString(), "rpm", "⚡"))
            } else {
                Result.success(SensorData("RPM", "--", "rpm", "⚡"))
            }
        } catch (e: Exception) {
            Result.success(SensorData("RPM", "--", "rpm", "⚡"))
        }
    }

    suspend fun getSpeed(): Result<SensorData> {
        return try {
            val raw = sendOBDCommand("010D")
            val value = parseHexResponse(raw)
            if (value >= 0) {
                Result.success(SensorData("Speed", value.toString(), "km/h", "🚗"))
            } else {
                Result.success(SensorData("Speed", "--", "km/h", "🚗"))
            }
        } catch (e: Exception) {
            Result.success(SensorData("Speed", "--", "km/h", "🚗"))
        }
    }

    suspend fun getCoolantTemp(): Result<SensorData> {
        return try {
            val raw = sendOBDCommand("0105")
            val value = parseHexResponse(raw)
            if (value >= 0) {
                Result.success(SensorData("Coolant", "${value - 40}°", "C", "🌡️"))
            } else {
                Result.success(SensorData("Coolant", "--", "C", "🌡️"))
            }
        } catch (e: Exception) {
            Result.success(SensorData("Coolant", "--", "C", "🌡️"))
        }
    }

    suspend fun getBatteryVoltage(): Result<SensorData> {
        return try {
            val raw = sendOBDCommand("0142")
            val value = parseHexResponse(raw)
            if (value >= 0) {
                Result.success(SensorData("12V Batt", String.format("%.1f", value * 0.0793), "V", "🔋"))
            } else {
                Result.success(SensorData("12V Batt", "--", "V", "🔋"))
            }
        } catch (e: Exception) {
            Result.success(SensorData("12V Batt", "--", "V", "🔋"))
        }
    }

    suspend fun getFuelLevel(): Result<SensorData> {
        return try {
            val raw = sendOBDCommand("012F")
            val value = parseHexResponse(raw)
            if (value >= 0) {
                Result.success(SensorData("Fuel", "${value * 100 / 255}%", "", "⛽"))
            } else {
                Result.success(SensorData("Fuel", "--", "", "⛽"))
            }
        } catch (e: Exception) {
            Result.success(SensorData("Fuel", "--", "", "⛽"))
        }
    }

    suspend fun getEngineLoad(): Result<SensorData> {
        return try {
            val raw = sendOBDCommand("0104")
            val value = parseHexResponse(raw)
            if (value >= 0) {
                Result.success(SensorData("Engine Load", "${value * 100 / 255}%", "", "🔧"))
            } else {
                Result.success(SensorData("Engine Load", "--", "", "🔧"))
            }
        } catch (e: Exception) {
            Result.success(SensorData("Engine Load", "--", "", "🔧"))
        }
    }

    suspend fun getHybridData(): Result<HybridData> {
        return try {
            var soc = "--"
            var voltage = "--"
            var current = "--"
            var temp = "--"

            obd.sendCommand("ATSH 7E4")
            val raw = obd.sendCommand("2101")
            obd.sendCommand("ATSH 7E0")

            val hex = raw.replace(" ", "").replace("\r", "").replace("\n", "")
            val clean = hex.substringAfter("6101").substringAfter("41")

            if (clean.length >= 2) {
                val socRaw = clean.substring(0, 2).toIntOrNull(16)
                if (socRaw != null) {
                    soc = if (socRaw <= 100) "$socRaw%" else "${socRaw / 2.55}%"
                }
            }
            if (clean.length >= 4) {
                val vRaw = clean.substring(2, 4).toIntOrNull(16)
                if (vRaw != null) voltage = "${vRaw * 0.5} V"
            }
            if (clean.length >= 6) {
                val iRaw = clean.substring(4, 6).toIntOrNull(16)
                if (iRaw != null) current = "${(iRaw - 128) * 0.1} A"
            }
            if (clean.length >= 8) {
                val tRaw = clean.substring(6, 8).toIntOrNull(16)
                if (tRaw != null) temp = "${tRaw - 40} °C"
            }

            Result.success(HybridData(soc, "--", "--", voltage, current, temp))
        } catch (e: Exception) {
            Result.success(HybridData())
        }
    }

    suspend fun getHybridBlockVoltages(): Result<BatteryPackData> {
        return try {
            obd.sendCommand("ATSH 7E4")
            val raw = obd.sendCommand("2103")
            obd.sendCommand("ATSH 7E0")

            val hex = raw.replace(" ", "").replace("\r", "").replace("\n", "")
            val clean = hex.substringAfter("6103").substringAfter("41")

            val blocks = mutableListOf<BlockVoltage>()
            for (i in 0 until minOf(14, clean.length / 2)) {
                val byteStr = clean.substring(i * 2, i * 2 + 2)
                val value = byteStr.toIntOrNull(16)
                if (value != null) {
                    val voltage = value * 0.5
                    blocks.add(BlockVoltage(i + 1, voltage))
                }
            }

            if (blocks.isEmpty()) {
                return Result.success(BatteryPackData())
            }

            val voltages = blocks.map { it.voltage }
            val avg = voltages.average()
            val max = voltages.max()
            val min = voltages.min()
            val delta = (max - min) * 1000.0
            val total = avg * 14

            val blocksWithDev = blocks.map { it.copy(deviation = kotlin.math.abs(it.voltage - avg)) }

            val health = when {
                delta < 50 -> "PERFECT"
                delta < 100 -> "GOOD"
                delta < 200 -> "WARNING"
                else -> "CRITICAL"
            }

            val analysis = when {
                delta < 50 -> "✅ Excellent balance! All cells within 50mV."
                delta < 100 -> "✓ Good balance. Delta: ${"%.0f".format(delta)}mV."
                delta < 200 -> "⚠️ Noticeable imbalance (${"%.0f".format(delta)}mV). Check high/low blocks."
                else -> "🔴 Critical imbalance (${"%.0f".format(delta)}mV). Service needed!"
            }

            Result.success(BatteryPackData(
                blocks = blocksWithDev,
                avgVoltage = avg,
                maxVoltage = max,
                minVoltage = min,
                deltaMv = delta,
                totalVoltage = total,
                healthStatus = health,
                analysis = analysis,
            ))
        } catch (e: Exception) {
            Result.success(BatteryPackData())
        }
    }

    suspend fun getDiagnosticTroubleCodes(): Result<List<DiagnosticTroubleCode>> {
        return try {
            val raw = obd.sendCommand("03")
            val codes = mutableListOf<DiagnosticTroubleCode>()

            val parts = raw.split(" ")
            for (i in parts.indices step 3) {
                if (i + 2 < parts.size) {
                    val first = parts[i].toIntOrNull(16) ?: continue
                    val second = parts[i + 1].toIntOrNull(16) ?: continue
                    if (first in 0x41..0x4F || first in 0x81..0x8F) continue
                    val prefix = when ((first and 0xC0) shr 6) {
                        0 -> "P"; 1 -> "C"; 2 -> "B"; 3 -> "U"; else -> "?"
                    }
                    val type = when ((first and 0x30) shr 4) {
                        0 -> "0"; 1 -> "1"; 2 -> "2"; 3 -> "3"; else -> "?"
                    }
                    val code = String.format("%s%s%02X%02X", prefix, type, (first and 0x0F), second)
                    if (code.length in 5..6) codes.add(DiagnosticTroubleCode(code))
                }
            }
            Result.success(codes.distinct())
        } catch (e: Exception) {
            Result.success(emptyList())
        }
    }

    suspend fun clearDiagnosticTroubleCodes(): Result<Unit> {
        return try {
            obd.sendCommand("04")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun sendOBDCommand(pid: String): String = obd.sendCommand(pid)

    private fun parseHexResponse(raw: String): Int {
        val cleaned = raw.replace(" ", "").replace("\r", "").replace("\n", "")
        if (cleaned.length < 4) return -1
        val dataPart = cleaned.substringAfter("41")
        if (dataPart.length < 4) return -1
        return dataPart.substring(2, 4).toIntOrNull(16) ?: -1
    }

    companion object {
        val DTC_DESCRIPTIONS = mapOf(
            "P0A80" to "Replace hybrid battery pack",
            "P0A7F" to "Hybrid battery pack deterioration",
            "P3000" to "Battery control system malfunction",
            "P3100" to "Hybrid battery low",
            "P0A0F" to "Engine failed to start (hybrid)",
            "P0A1B" to "DC/DC converter performance",
            "P0A3F" to "Motor/generator position sensor",
            "P0A78" to "Inverter coolant pump",
            "P0AA6" to "HV battery voltage system",
            "P0AC4" to "Hybrid powertrain control module",
            "P0A09" to "DC/DC converter status",
            "P0A37" to "MG2 temperature sensor",
            "P0560" to "System voltage low",
            "P3190" to "Engine poor starting (hybrid)",
        )
    }
}
