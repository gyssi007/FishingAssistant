package com.fishtime.assistant.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object ApiClient {

    val client: OkHttpClient by lazy {

        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
