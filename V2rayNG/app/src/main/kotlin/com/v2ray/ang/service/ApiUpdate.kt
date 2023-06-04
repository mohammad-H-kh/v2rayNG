package com.v2ray.ang.service

import com.google.gson.annotations.SerializedName

data class GetDataRequest(val email: String, val password: String)

data class GetDataResponse(
    val success: Boolean,
    val msg: String,
    val obj: GetDataResponseObject
)

data class GetDataResponseObject(
    val id: Int,
    @SerializedName("inboundId")
    val inboundID: Int,
    val enable: Boolean,
    val email: String,
    val up: Long,
    val down: Long,
    val expiryTime: Long,
    val total: Long
)