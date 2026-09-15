package com.antidoomscroller.core.policy

import com.antidoomscroller.core.model.BlockStyle
import com.antidoomscroller.core.model.FeedSurface

/** Why the guard let a screen through - shown verbatim in the app's live-status readout. */
enum class AllowReason {
    MASTER_SWITCH_OFF,
    APP_NOT_GUARDED,
    SCHEDULED_BREAK,

    /** The user spent today's allowance and is inside it. */
    SCROLL_PASS,
    SURFACE_ALLOWED,
    SURFACE_UNRECOGNISED,

    /** The screen is not a short-video feed, so it is not something this app touches. */
    NOT_SHORT_VIDEO,
}

sealed interface GuardDecision {

    data class Allow(
        val reason: AllowReason,
        val surface: FeedSurface = FeedSurface.UNKNOWN,
        val detail: String? = null,
    ) : GuardDecision

    data class Block(
        val packageName: String,
        val surface: FeedSurface,
        val style: BlockStyle = BlockStyle.COVER,
        val evidence: List<String> = emptyList(),
        /** True once the host app has re-shown a blocked screen too many times in a row. */
        val escapeToHome: Boolean = false,
    ) : GuardDecision
}
