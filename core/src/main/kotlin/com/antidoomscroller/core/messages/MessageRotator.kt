package com.antidoomscroller.core.messages

import com.antidoomscroller.core.model.FeedSurface
import com.antidoomscroller.core.model.MessageKind
import com.antidoomscroller.core.model.MessageSettings

/**
 * Keeps a message on screen long enough to be read.
 *
 * The screen behind a cover is re-examined several times a second, and each look used to pick a
 * message of its own - so a randomised list flickered past instead of saying anything. A message
 * is chosen once and then held: it only changes when the hold has elapsed, or when the cover is
 * for something else entirely.
 */
class MessageRotator(private val book: MessageBook = MessageBook()) {

    private var current: RenderedMessage? = null
    private var currentKey: String? = null
    private var pickedAtMs: Long = 0

    /**
     * @param key what the message is for - the app and surface being blocked. A new key is a new
     *   interruption and gets its own message straight away.
     */
    fun render(
        settings: MessageSettings,
        kind: MessageKind,
        key: String,
        nowMs: Long,
        appLabel: String? = null,
        surface: FeedSurface? = null,
    ): RenderedMessage {
        val held = current
        val expired = nowMs - pickedAtMs >= settings.holdMs
        if (held != null && key == currentKey && !expired) return held

        val rendered = book.render(settings, kind, appLabel, surface)
        current = rendered
        currentKey = key
        pickedAtMs = nowMs
        return rendered
    }

    /** Called when nothing is being shown, so the next interruption starts fresh. */
    fun clear() {
        current = null
        currentKey = null
        pickedAtMs = 0
    }
}
