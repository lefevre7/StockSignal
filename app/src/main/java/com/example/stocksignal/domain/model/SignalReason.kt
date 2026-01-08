package com.example.stocksignal.domain.model

data class SignalReason(
    val id: String,
    val title: String,
    val explanation: String,
    val impactScore: Int,
    val model: String?
)
