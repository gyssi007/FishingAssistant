package com.fishtime.assistant.network

data class MerchantResponse(
    val code: String,
    val data: MerchantData
)

data class MerchantData(
    val list: List<Merchant>
)
