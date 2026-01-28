package com.example.stocksignal.data.stooq.network

import java.io.IOException

class StooqBlockedException(
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)
