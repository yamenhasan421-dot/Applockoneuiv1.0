package com.example.domain

import android.graphics.drawable.Drawable
import android.graphics.Bitmap

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null,
    val iconBitmap: Bitmap? = null
)

