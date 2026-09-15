# How a feed is recognised, and what to do when it stops being recognised

Instagram and YouTube rename their internal view ids between releases. When that happens a feed
stops being detected and the guard silently lets it through. This is the file to come back to.

## The model

A screen is flattened into a **snapshot**: the set of view ids on it (with the `com.instagram.
android:id/` prefix stripped), plus content descriptions and visible text, all lowercased. That
snapshot is scored against a **signature pack** — a list of rules, one per surface, that say which
id fragments must be present, which must be absent, and which navigation context must be active.

Matching is by *fragment*, not exact id: `clips_viewer` matches `clips_viewer_root`,
`clips_viewer_video_container` and anything else in that family. That is what makes the rules
survive most renames.

The four surfaces that can be blocked are all short video:

| Surface | What it means |
| --- | --- |
| `SHORT_VIDEO_FEED` | The Reels tab / the Shorts player |
| `SHORT_VIDEO_IN_HOME` | A reels unit embedded in the main timeline |
| `SHORT_VIDEO_IN_EXPLORE` | A reel opened from Explore, or a short from search |
| `SHORT_VIDEO_IN_DM` | A reel opened out of a message thread |

`HOME_FEED`, `EXPLORE` and `STORIES` are also detected, but only so the guard knows where the user
is. They are refused at the policy layer and cannot be blocked.

## Telling apart four screens that look identical

Instagram's reel player is the same fragment whether you reached it from the Reels tab, from
Explore, or from a friend's message. Two independent signals separate them, and the app uses both
because either alone has a failure mode.

**The navigation trail.** As the user moves around, screens that carry a context marker — a DM
thread, the explore grid — record a tag. Tags expire after six seconds, and any screen that carries
its own markers clears the previous ones. So a reel opened while the DM tag is live is a DM reel.

The failure mode: leave a DM, tap the Reels tab within six seconds, and the stale tag would still
say "DM".

**The bottom tab bar.** The Reels tab keeps Instagram's navigation bar on screen. A reel pushed from
a message thread is a full-screen fragment with no tab bar. So a signature that requires
`clips_tab`/`tab_bar` to be present scores above every context rule, and a reel-in-DM signature
requires it to be *absent*.

Together: the tab bar settles the ambiguous case, and the trail handles everything the tab bar
cannot see. `DetectionTest` pins both, including the "straight out of a DM into the Reels tab" case.

## When a feed stops being caught

1. **Confirm which surface is missed.** Instagram's Reels tab and a reel inside the home feed are
   different signatures; only one may have broken.
2. **Find the new ids.** With the app connected over adb:
   `adb shell uiautomator dump && adb shell cat /sdcard/window_dump.xml`, then look at
   `resource-id` on the containers around the video. You want a container id, not a leaf.
3. **Add the fragment** to the relevant signature in
   `core/src/main/kotlin/com/antidoomscroller/core/detect/DefaultSignatures.kt`. Prefer the
   shortest stable-looking fragment; keep the old one, since not everyone updates at once.
4. **Add a test** in `core/src/test/kotlin/com/antidoomscroller/core/DetectionTest.kt` — one line
   with the new id — and run `./gradlew :core:test`.

No device is needed for step 4: the classifier takes a plain data snapshot, so a regression can be
reproduced from the ids alone.

## Signature fields

```kotlin
SurfaceSignature(
    surface = FeedSurface.SHORT_VIDEO_FEED.id,
    weight = 130,                       // highest scoring match wins
    anyViewId = listOf("reel_recycler"), // at least one must be present
    allViewId = listOf("clips_viewer"),  // every one must be present
    noneViewId = listOf("tab_bar"),      // none may be present
    anyContentDescription = listOf("shorts"),
    anyText = listOf(),
    requireContext = listOf(ContextTag.DM),   // navigation tags that must be live
    forbidContext = listOf(ContextTag.DM),    // and ones that disqualify the rule
)
```

A signature must have at least one positive matcher; a rule made only of context requirements would
match every screen in that context and is rejected.

## Replacing the pack without a build

The pack is serialised JSON (`SignaturePack.encode` / `SignaturePack.parse`) and stored in the app's
settings, so a corrected pack can be written to `PrefKeys.SIGNATURES` and picked up live — the
classifier re-reads it from a flow. *About → Reset to built-in signatures* restores whatever the
installed build shipped with.

## YouTube notes

YouTube's ids are heavily obfuscated and change more often than Instagram's, so its signatures lean
on content descriptions ("shorts") as well as ids. `reel_recycler` and `reel_player_page_container`
have been stable across many releases and are the primary markers. The Shorts *shelf* on the home
tab is distinguished from the *player* by requiring the player ids to be absent.
