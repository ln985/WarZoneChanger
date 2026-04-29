package com.warzone.changer.model

import com.google.gson.annotations.SerializedName

data class Announcement(
    @SerializedName("title") val title: String = "",
    @SerializedName("content") val content: String = "",
    @SerializedName("active") val active: Boolean = true,
    @SerializedName("time") val time: String = ""
)