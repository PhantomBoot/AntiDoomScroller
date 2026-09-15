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
    /**
     * Returns the matched evidence, or null when the signature does not apply.
     *
     * `allViewId` is a precondition, not evidence: a signature that names the list a reels unit
     * sits in must still find the unit itself, or it would match every screen showing that list -
     * which is how ordinary photo posts ended up being covered. So a positive hit has to come
     * from one of the `any*` groups whenever any of them is specified.
     */
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

        val viewIdHit = signature.anyViewId.firstOrNull { snapshot.hasViewIdContaining(it) }
        val descriptionHit = signature.anyContentDescription.firstOrNull { snapshot.hasDescriptionContaining(it) }
        val textHit = signature.anyText.firstOrNull { snapshot.hasTextContaining(it) }

        val groups = listOf(
            signature.anyViewId to viewIdHit?.let { "id~$it" },
            signature.anyContentDescription to descriptionHit?.let { "desc~$it" },
            signature.anyText to textHit?.let { "text~$it" },
        ).filter { (fragments, _) -> fragments.isNotEmpty() }

        if (groups.isEmpty()) {
            // Nothing but preconditions: the allViewId list is itself the identification.
            if (signature.allViewId.isEmpty()) return null
        } else {
            val hits = groups.mapNotNull { (_, hit) -> hit }
            val satisfied = if (signature.requireAllMatchers) hits.size == groups.size else hits.isNotEmpty()
            if (!satisfied) return null
            evidence += hits
        }

        if (signature.requireContext.isNotEmpty()) {
            evidence += signature.requireContext.map { "ctx:$it" }
        }
        return evidence
    }
}
