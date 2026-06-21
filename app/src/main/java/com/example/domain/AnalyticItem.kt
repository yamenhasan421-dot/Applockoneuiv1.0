package com.example.domain

import android.graphics.Bitmap

data class AnalyticItem(
    val packageName: String,
    val appName: String,
    val iconBitmap: Bitmap?,
    val unlockCount: Int
)
