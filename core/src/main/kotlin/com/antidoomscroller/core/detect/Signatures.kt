package com.antidoomscroller.core.detect

import com.antidoomscroller.core.model.FeedSurface
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One rule for recognising a surface.
 *
 * Signatures are data, not code, so they ship as an asset and can be replaced from the settings
 * screen when a host app renames its view ids - which they do, often.
 */
@Serializable
data class SurfaceSignature(
    /** [FeedSurface.id] this signature identifies. */
    val surface: String,
    /** Higher wins when several signatures match the same screen. */
    val weight: Int = 100,
    /** Any one of these view-id fragments must be present. */
    val anyViewId: List<String> = emptyList(),
    /** Every one of these view-id fragments must be present. */
    val allViewId: List<String> = emptyList(),
    /** None of these view-id fragments may be present. */
    val noneViewId: List<String> = emptyList(),
    val anyContentDescription: List<String> = emptyList(),
    val anyText: List<String> = emptyList(),
    /**
     * Normally one of the `any*` groups hitting is enough. Set this when a surface is only itself
     * when several independent things are true at once - a Shorts shelf, for instance, is a shelf
     * id *and* a "shorts" label, because the shelf id alone is every other shelf on the page.
     */
    val requireAllMatchers: Boolean = false,
    /** Context tags (see [com.antidoomscroller.core.model.ContextTag]) that must be active. */
    val requireContext: List<String> = emptyList(),
    /** Context tags that disqualify this signature. */
    val forbidContext: List<String> = emptyList(),
) {
    val feedSurface: FeedSurface? get() = FeedSurface.fromId(surface)
}

/** Signatures for one host app, plus the view ids that mark navigation context and chrome. */
@Serializable
data class AppSignature(
    val packageName: String,
    val displayName: String = packageName,
    /** context tag -> view-id fragments that mean "the user is in this part of the app". */
    val contextViewIds: Map<String, List<String>> = emptyMap(),
    val surfaces: List<SurfaceSignature> = emptyList(),
    /**
     * View-id fragments for the video itself, most specific first. The cover is sized to the
     * first of these that is actually on screen, so it lands on the video rather than the app.
     */
    val mediaViewIds: List<String> = emptyList(),
    /** The app's own bottom navigation bar, which the cover must never sit on top of. */
    val bottomChromeViewIds: List<String> = emptyList(),
    /** The app's own top bar, likewise. */
    val topChromeViewIds: List<String> = emptyList(),
)

@Serializable
data class SignaturePack(
    val version: Int = 1,
    val apps: List<AppSignature> = emptyList(),
) {
    fun forPackage(packageName: String): AppSignature? = apps.firstOrNull { it.packageName == packageName }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun parse(raw: String): SignaturePack = json.decodeFromString(serializer(), raw)

        fun encode(pack: SignaturePack): String = json.encodeToString(serializer(), pack)
    }
}
