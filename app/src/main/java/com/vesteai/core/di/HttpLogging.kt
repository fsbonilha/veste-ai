package com.vesteai.core.di

import okhttp3.Interceptor

/**
 * Variant-specific interceptors for the provider's HTTP client (plan §6.4). Debug binds a logging
 * interceptor; release binds none. The seam exists because `okhttp-logging` is a `debugImplementation`
 * dependency, so release cannot even compile a reference to it.
 */
fun interface HttpLogging {
    fun interceptors(): List<Interceptor>
}