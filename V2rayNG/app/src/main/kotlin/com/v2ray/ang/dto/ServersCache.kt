package com.v2ray.ang.dto

data class ServersCache(
    val guid: String,
    val config: ServerConfig,
    var tapUsage: String = "Tap To Update",
    var tapExpire: String = "Tap To Update"
)