package com.code7.techupdates

/** Human-readable version label, e.g. "v0.1.0 (1)". */
fun versionLabel(
    versionName: String,
    versionCode: Int,
): String = "v$versionName ($versionCode)"
