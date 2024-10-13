package org.taymyr.lagom.soap.interceptor

data class ShadowingSettings(
    val soapMethod: String,
    val replacingSymbols: String,
    val requestPatterns: List<String>,
    val responsePatterns: List<String>
)
