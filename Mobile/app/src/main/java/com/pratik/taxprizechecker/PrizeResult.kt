package com.pratik.taxprizechecker

data class PrizeResult(
    val coupon: String,
    val rank: Int,
    val category: String,
    val title: String,
    val eligibleFrom: String,
    val eligibleTo: String
)