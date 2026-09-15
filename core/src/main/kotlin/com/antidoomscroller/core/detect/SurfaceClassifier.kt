package com.antidoomscroller.core.detect

import com.antidoomscroller.core.model.FeedSurface

/** What the classifier decided, and why - the evidence is surfaced in the app's debug screen. */
data class Classification(
    val surface: FeedSurface,
    val score: Int,
    val evidence: List<String> = emptyList(),
    val contextTags: Set<String> = emptySet(),
) {
    val isRecognised: Boolean get() = surface != FeedSurface.UNKNOWN

    companion object {
        fun unknown(contextTags: Set<String> = emptySet()): Classification =
            Classification(FeedSurface.UNKNOWN, 0, emptyList(), contextTags)
    }
}

/**
 * Scores a [ScreenSnapshot] against a [SignaturePack].
 *
 * Signatures that require context (`a reel opened from a DM`) are scored above generic ones, so a
 * reel in a DM thread is never mistaken for the Reels tab when DM reels are allowed.
 */
class SurfaceClassifier(private var pack: SignaturePack) {

    fun updatePack(newPack: SignaturePack) {
        pack = newPack
    }

    fun currentPack(): SignaturePack = pack

    fun signatureFor(packageName: String): AppSignature? = pack.forPackage(packageName)

    /** Context tags implied by the snapshot itself, e.g. a visible DM thread. */
    fun contextTagsFor(snapshot: ScreenSnapshot): Set<String> {
        val app = pack.forPackage(snapshot.packageName) ?: return emptySet()
        return app.contextViewIds
            .filterValues { fragments -> fragments.any { snapshot.hasViewIdContaining(it) } }
            .keys
    }

    fun classify(snapshot: ScreenSnapshot, activeContext: Set<String>): Classification {
        val app = pack.forPackage(snapshot.packageName) ?: return Classification.unknown(activeContext)

        var best: Classification? = null
        for (signature in app.surfaces) {
            val surface = signature.feedSurface ?: continue
            val evidence = match(signature, snapshot, activeContext) ?: continue
            // Context-qualified signatures outrank generic ones at equal weight.
            val score = signature.weight + signature.requireContext.size * 5 + evidence.size
            if (best == null || score > best.score) {
                best = Classification(surface, score, evidence, activeContext)
            }
        }
        return best ?: Classification.unknown(activeContext)
    }

    /** Returns the matched evidence, or null when the signature does not apply. */
    private fun match(
        signature: SurfaceSignature,
        snapshot: ScreenSnapshot,
        activeContext: Set<String>,
    ): List<String>? {
        if (signature.forbidContext.any { it in activeContext }) return null
        if (signature.requireContext.any { it !in activeContext }) return null
        if (signature.noneViewId.any { snapshot.hasViewIdContaining(it) }) return null

        val evidence = mutableListOf<String>()

        for (fragment in signature.allViewId) {
            if (!snapshot.hasViewIdContaining(fragment)) return null
            evidence += "id~$fragment"
        }

        var positiveMatched = signature.allViewId.isNotEmpty()

        if (signature.anyViewId.isNotEmpty()) {
            val hit = signature.anyViewId.firstOrNull { snapshot.hasViewIdContaining(it) }
            if (hit != null) {
                evidence += "id~$hit"
                positiveMatched = true
            } else if (signature.anyContentDescription.isEmpty() && signature.anyText.isEmpty()) {
                return null
            }
        }

        if (signature.anyContentDescription.isNotEmpty()) {
            val hit = signature.anyContentDescription.firstOrNull { snapshot.hasDescriptionContaining(it) }
            if (hit != null) {
                evidence += "desc~$hit"
                positiveMatched = true
            } else if (signature.anyViewId.isEmpty() && signature.anyText.isEmpty()) {
                return null
            }
        }

        if (signature.anyText.isNotEmpty()) {
            val hit = signature.anyText.firstOrNull { snapshot.hasTextContaining(it) }
            if (hit != null) {
                evidence += "text~$hit"
                positiveMatched = true
            } else if (signature.anyViewId.isEmpty() && signature.anyContentDescription.isEmpty()) {
                return null
            }
        }

        // A signature made only of context requirements would match every screen in that context.
        if (!positiveMatched) return null
        if (signature.requireContext.isNotEmpty()) {
            evidence += signature.requireContext.map { "ctx:$it" }
        }
        return evidence
    }
}
