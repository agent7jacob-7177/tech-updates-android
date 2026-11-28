package com.code7.techupdates

/** Human-readable version label, e.g. "v0.1.0 (1)". */
fun versionLabel(
    versionName: String,
    versionCode: Int,
): String = "v$versionName ($versionCode)"

/** Links shown on the Settings / About screen. */
object AppLinks {
    const val SOURCE_REPO = "https://github.com/agent7jacob-7177/tech-updates-android"
    const val PRIVACY_POLICY = "https://agent7jacob-7177.github.io/tech-updates-android/privacy.html"
}
