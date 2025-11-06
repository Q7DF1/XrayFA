package com.android.xrayfa.http

import com.android.xrayfa.parser.SubscriptionParser
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class HttpTest {


    lateinit var okHttpClient: OkHttpClient
    @Before
    fun setup() {
        val interceptor = Interceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header("Accept","*/*")
                .header("User-Agent", "xrayFA/1.0")
                .method(original.method, original.body)
                .build()
            chain.proceed(request)
        }
        okHttpClient = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}