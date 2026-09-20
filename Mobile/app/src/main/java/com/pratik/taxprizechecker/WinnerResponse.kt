package com.pratik.taxprizechecker

data class WinnerResponse(
    val has_more: Boolean,
    val draws: List<Draw>
)

data class Draw(
    val category_title_en: String,
    val title_en: String,
    val eligible_from: String,
    val eligible_to: String,
    val winners: List<Winner>
)

data class Winner(
    val winner_rank: Int,
    val prize_coupon_number: String
)