package com.camryobd.models

data class BlockVoltage(
    val block: Int,
    val voltage: Double,
    val deviation: Double = 0.0,
) {
    val status: CellStatus get() = when {
        deviation < 0.05 -> CellStatus.OPTIMAL
        deviation < 0.10 -> CellStatus.GOOD
        deviation < 0.20 -> CellStatus.WARN
        deviation < 0.50 -> CellStatus.BAD
        else -> CellStatus.CRITICAL
    }
}

enum class CellStatus {
    OPTIMAL, GOOD, WARN, BAD, CRITICAL
}

data class BatteryPackData(
    val blocks: List<BlockVoltage>,
    val avgVoltage: Double = 0.0,
    val maxVoltage: Double = 0.0,
    val minVoltage: Double = 0.0,
    val deltaMv: Double = 0.0,
    val totalVoltage: Double = 0.0,
    val healthStatus: String = "--",
    val analysis: String = "",
)
