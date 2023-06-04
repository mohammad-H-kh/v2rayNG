package com.v2ray.ang.service

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("cpsess7945419007/frontend/jupiter/api/v1/getData")
    fun login(@Body request: GetDataRequest): Call<GetDataResponse>
}
