package com.camryobd.models

data class SensorData(
    val name: String,
    val value: String,
    val unit: String,
    val icon: String = "",
)

data class HybridData(
    val hvBatterySoc: String = "--",
    val mg1Rpm: String = "--",
    val mg2Rpm: String = "--",
    val hvBatteryVoltage: String = "--",
    val hvBatteryCurrent: String = "--",
    val hvBatteryTemp: String = "--",
)
