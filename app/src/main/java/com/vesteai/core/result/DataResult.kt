package com.vesteai.core.result

sealed interface DataResult<out T> {
    data class Success<out T>(val value: T) : DataResult<T>
    data class Failure(val error: Throwable) : DataResult<Nothing>
}
