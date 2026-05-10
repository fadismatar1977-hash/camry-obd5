package com.camryobd

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.camryobd.models.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Initializing : ConnectionState()
    object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

data class DashboardState(
    val rpm: SensorData = SensorData("RPM", "--", "rpm", "⚡"),
    val speed: SensorData = SensorData("Speed", "--", "km/h", "🚗"),
    val coolant: SensorData = SensorData("Coolant", "--", "C", "🌡️"),
    val battery12V: SensorData = SensorData("12V Batt", "--", "V", "🔋"),
    val fuel: SensorData = SensorData("Fuel", "--", "", "⛽"),
    val load: SensorData = SensorData("Engine Load", "--", "", "🔧"),
    val hybridData: HybridData = HybridData(),
    val evPercentage: String = "--",
    val timer0to100: String = "--"
)

class ObdViewModel : ViewModel() {
    private val obdService = OBDService()
    private val manager = OBD2Manager(obdService)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _dashboardState = MutableStateFlow(DashboardState())
    val dashboardState: StateFlow<DashboardState> = _dashboardState.asStateFlow()

    private val _batteryState = MutableStateFlow(BatteryPackData())
    val batteryState: StateFlow<BatteryPackData> = _batteryState.asStateFlow()

    private val _dtcState = MutableStateFlow<List<DiagnosticTroubleCode>?>(null)
    val dtcState: StateFlow<List<DiagnosticTroubleCode>?> = _dtcState.asStateFlow()
    
    private val _dtcMessage = MutableStateFlow<String>("")
    val dtcMessage: StateFlow<String> = _dtcMessage.asStateFlow()

    private var dashboardJob: Job? = null
    private var batteryJob: Job? = null

    fun connect(address: String) {
        _connectionState.value = ConnectionState.Connecting
        viewModelScope.launch {
            val result = obdService.connect(address)
            if (result.isSuccess) {
                _connectionState.value = ConnectionState.Initializing
                val initResult = manager.initialize()
                if (initResult.isSuccess) {
                    _connectionState.value = ConnectionState.Connected
                    scanDTCs() // Auto-fetch fault codes once on connection
                } else {
                    _connectionState.value = ConnectionState.Error("Init failed: ${initResult.exceptionOrNull()?.message}")
                }
            } else {
                _connectionState.value = ConnectionState.Error("Failed: ${result.exceptionOrNull()?.message}")
            }
        }
    }
    
    fun disconnect() {
        obdService.disconnect()
        _connectionState.value = ConnectionState.Disconnected
    }

    private var totalActiveSeconds = 0L
    private var evSeconds = 0L
    private var timerState = 0 // 0: Idle, 1: Ready(Speed=0), 2: Running
    private var timerStartMs = 0L
    private var last0To100Time = "--"

    fun startDashboardPolling() {
        dashboardJob?.cancel()
        dashboardJob = viewModelScope.launch {
            while (isActive && _connectionState.value == ConnectionState.Connected) {
                val rpm = manager.getEngineRpm().getOrDefault(SensorData("RPM", "--", "rpm", "⚡"))
                val speed = manager.getSpeed().getOrDefault(SensorData("Speed", "--", "km/h", "🚗"))
                val coolant = manager.getCoolantTemp().getOrDefault(SensorData("Coolant", "--", "C", "🌡️"))
                val battery12V = manager.getBatteryVoltage().getOrDefault(SensorData("12V Batt", "--", "V", "🔋"))
                val fuel = manager.getFuelLevel().getOrDefault(SensorData("Fuel", "--", "", "⛽"))
                val load = manager.getEngineLoad().getOrDefault(SensorData("Engine Load", "--", "", "🔧"))
                val hybridData = manager.getHybridData().getOrDefault(HybridData())

                // EV Tracker Logic
                val rpmVal = rpm.value.toIntOrNull()
                val speedVal = speed.value.toIntOrNull()

                if (rpmVal != null && speedVal != null) {
                    if (rpmVal > 0 || speedVal > 0) totalActiveSeconds++
                    if (rpmVal == 0 && speedVal > 0) evSeconds++
                }
                
                val evPct = if (totalActiveSeconds > 0) {
                    "${(evSeconds * 100 / totalActiveSeconds)}%"
                } else {
                    "--"
                }

                // 0-100 Timer Logic
                if (speedVal != null) {
                    if (speedVal == 0) {
                        timerState = 1 // Ready
                    } else if (speedVal > 0 && timerState == 1) {
                        timerState = 2 // Running
                        timerStartMs = System.currentTimeMillis()
                        last0To100Time = "Timing..."
                    } else if (speedVal >= 100 && timerState == 2) {
                        val elapsed = (System.currentTimeMillis() - timerStartMs) / 1000.0
                        last0To100Time = String.format("%.2fs", elapsed)
                        timerState = 0 // Finished
                    }
                }

                _dashboardState.value = DashboardState(
                    rpm, speed, coolant, battery12V, fuel, load, hybridData, evPct, last0To100Time
                )
                delay(1000)
            }
        }
    }

    fun stopDashboardPolling() {
        dashboardJob?.cancel()
        dashboardJob = null
    }

    fun startBatteryPolling() {
        batteryJob?.cancel()
        batteryJob = viewModelScope.launch {
            while (isActive && _connectionState.value == ConnectionState.Connected) {
                val pack = manager.getHybridBlockVoltages().getOrDefault(BatteryPackData())
                _batteryState.value = pack
                delay(3000)
            }
        }
    }

    fun stopBatteryPolling() {
        batteryJob?.cancel()
        batteryJob = null
    }

    fun scanDTCs() {
        _dtcMessage.value = "Scanning..."
        viewModelScope.launch {
            if (_connectionState.value != ConnectionState.Connected) {
                _dtcMessage.value = "Not connected"
                return@launch
            }
            val result = manager.getDiagnosticTroubleCodes()
            if (result.isSuccess) {
                val codes = result.getOrThrow()
                _dtcState.value = codes
                if (codes.isEmpty()) {
                    _dtcMessage.value = "No fault codes found ✓"
                } else {
                    _dtcMessage.value = "Found ${codes.size} codes"
                }
            } else {
                _dtcMessage.value = "Error: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun clearDTCs() {
        _dtcMessage.value = "Clearing codes..."
        viewModelScope.launch {
            if (_connectionState.value != ConnectionState.Connected) {
                _dtcMessage.value = "Not connected"
                return@launch
            }
            val result = manager.clearDiagnosticTroubleCodes()
            if (result.isSuccess) {
                _dtcState.value = emptyList()
                _dtcMessage.value = "Codes cleared ✓"
            } else {
                _dtcMessage.value = "Clear failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        obdService.disconnect()
    }
}
