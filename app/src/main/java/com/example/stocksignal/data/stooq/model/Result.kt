package com.example.stocksignal.data.stooq.model

/**
 * Sealed class representing the result of an operation.
 * Used for error handling in a type-safe, functional way.
 *
 * @param T The type of successful result data
 */
sealed class Result<out T> {
    /**
     * Successful result containing data
     */
    data class Success<T>(val data: T) : Result<T>()

    /**
     * Error result containing exception and optional message
     */
    data class Error(
        val exception: Throwable,
        val message: String = exception.message ?: "Unknown error occurred"
    ) : Result<Nothing>()

    /**
     * Check if result is successful
     */
    val isSuccess: Boolean
        get() = this is Success

    /**
     * Check if result is error
     */
    val isError: Boolean
        get() = this is Error

    /**
     * Get data if successful, null otherwise
     */
    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }

    /**
     * Get data if successful, or throw exception if error
     */
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw exception
    }
}
