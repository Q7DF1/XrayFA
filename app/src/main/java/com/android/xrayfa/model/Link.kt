package com.android.xrayfa.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Link(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val protocol: String,
    val content: String,
    val selected: Boolean = false
)
