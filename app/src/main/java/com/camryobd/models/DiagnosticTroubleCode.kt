package com.camryobd.models

data class DiagnosticTroubleCode(
    val code: String,
    val description: String = "",
    val isConfirmed: Boolean = true,
)
