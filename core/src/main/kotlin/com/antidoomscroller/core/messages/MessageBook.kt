package com.antidoomscroller.core.messages

import com.antidoomscroller.core.model.FeedSurface
import com.antidoomscroller.core.model.MessageKind
import com.antidoomscroller.core.model.MessageRotation
import com.antidoomscroller.core.model.MessageSettings
import kotlin.random.Random

/** A rendered interruption: title plus the user's own words. */
data class RenderedMessage(
    val title: String,
    val body: String,
)

/**
 * Picks which of the user's custom messages to show.
 *
 * Every string here is user-authored; the built-in list is only a starting point and can be
 * deleted entirely. An empty list falls back to a single neutral line so the block card is never
 * blank.
 */
class MessageBook(private val random: Random = Random.Default) {

    private var sequentialIndex = 0

    fun pick(settings: MessageSettings, kind: MessageKind): String {
        val messages = settings.messagesFor(kind).filter { it.isNotBlank() }
        if (messages.isEmpty()) return FALLBACK
        return when (settings.rotation) {
            MessageRotation.FIRST -> messages.first()
            MessageRotation.RANDOM -> messages[random.nextInt(messages.size)]
            MessageRotation.SEQUENTIAL -> {
                val chosen = messages[sequentialIndex % messages.size]
                sequentialIndex = (sequentialIndex + 1) % messages.size
                chosen
            }
        }
    }

    fun render(
        settings: MessageSettings,
        kind: MessageKind,
        appLabel: String? = null,
        surface: FeedSurface? = null,
    ): RenderedMessage {
        val title = buildString {
            append(
                when (kind) {
                    MessageKind.FEED_BLOCK -> "Blocked"
                    MessageKind.ANTI_SCROLL -> "Still scrolling"
                    MessageKind.ADULT_BLOCK -> "Site blocked"
                },
            )
            if (settings.showAppName && !appLabel.isNullOrBlank()) append(" · ").append(appLabel)
            if (settings.showBlockedSurface && surface != null && surface != FeedSurface.UNKNOWN) {
                append(" · ").append(surface.label)
            }
        }
        return RenderedMessage(title = title, body = pick(settings, kind))
    }

    fun resetRotation() {
        sequentialIndex = 0
    }

    companion object {
        const val FALLBACK = "Blocked by AntiDoomScroller."

        /** Guards against pathological input before a message is saved. */
        fun sanitise(raw: String): String = raw.replace(Regex("\\s+"), " ").trim().take(MAX_LENGTH)

        const val MAX_LENGTH = 240
    }
}
