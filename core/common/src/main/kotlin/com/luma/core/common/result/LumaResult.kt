package com.luma.core.common.result

sealed interface LumaResult<out T> {
    data class Success<T>(val data: T) : LumaResult<T>
    data class Error(val exception: Throwable, val message: String? = null) : LumaResult<Nothing>
    data object Loading : LumaResult<Nothing>
}

inline fun <T, R> LumaResult<T>.map(transform: (T) -> R): LumaResult<R> = when (this) {
    is LumaResult.Success -> LumaResult.Success(transform(data))
    is LumaResult.Error -> this
    is LumaResult.Loading -> this
}

inline fun <T> LumaResult<T>.onSuccess(action: (T) -> Unit): LumaResult<T> {
    if (this is LumaResult.Success) action(data)
    return this
}

inline fun <T> LumaResult<T>.onError(action: (Throwable, String?) -> Unit): LumaResult<T> {
    if (this is LumaResult.Error) action(exception, message)
    return this
}
