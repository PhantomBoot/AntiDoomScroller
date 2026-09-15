# AntiDoomScroller

An Android app that removes short-form video feeds — Instagram Reels, YouTube Shorts — and leaves
everything else in those apps working exactly as before.

You can still send and read messages, watch stories, browse posts, search, and watch long-form
YouTube videos. The infinite vertical feed is the only thing that goes.

It also includes an optional adult content filter that works in every browser on the phone, where
turning it *off* costs a 48 hour wait and a set of arithmetic problems.

Everything happens on the device. No account, no server, no analytics.

---

## Install it on your phone

1. Open the [**Releases** page](https://github.com/PhantomBoot/AntiDoomScroller/releases) on your
   phone. This repository is private, so sign in to GitHub in your phone's browser first —
   otherwise the download link returns "not found". (Making the repository public removes that
   step, at the cost of the code being public too.)
2. Download `antidoomscroller.apk` from the release marked **Latest build**. It is rebuilt on every
   push, so that link always points at the newest version.
3. Open the downloaded file. Android will ask whether to allow installing apps from your browser —
   say yes, then install.
4. Open AntiDoomScroller and follow the two setup steps below.

Every build is signed with the same key, so a later build installs over the old one as an update
and your settings survive. (That key is the standard Android debug key, committed on purpose — see
[`keystore/README.md`](keystore/README.md). It is not a secret, and this build is for your own
phone rather than the Play Store.)

### Two things to switch on

**Accessibility permission** — this is how the app sees that a Reels or Shorts feed has opened. The
app will link you straight to *Settings → Accessibility → AntiDoomScroller*. Everything it reads is
examined on the device and dropped immediately; nothing is stored or sent.

**Battery** — so Android does not stop it in the background, exclude AntiDoomScroller from battery
optimisation. There is a button on the dashboard that opens the right settings screen. The guard is
a system-bound accessibility service, so it keeps running with the app closed and restarts itself
after a reboot.

The adult filter, if you want it, asks for one more permission (a local VPN) the first time you
switch it on.

---

## What it removes, and what it never touches

| Screen | What happens |
| --- | --- |
| Instagram Reels tab | Removed |
| A reel inside the home feed | That one unit is covered; the posts above and below stay usable |
| A reel opened from Explore | Removed |
| **A reel a friend sent you in a DM** | **Allowed** by default — one switch changes it |
| YouTube Shorts player and shelf | Removed |
| Direct messages, stories, posts, comments, search, profiles | Never touched |
| Long-form YouTube videos, subscriptions, playlists | Never touched |

That last row is not a default you could accidentally change. A screen that is not a short-video
feed is refused at the policy layer before any setting is consulted, and there is a test that keeps
it that way.

### Two ways to remove a feed

Each app has its own style, because the feeds are shaped differently:

- **Cover** (Instagram's default) — an opaque box goes over the video and the app carries on
  underneath. You stay exactly where you were. The box is sized to the video's own container, not
  the screen: a reel in your timeline keeps the post header above it readable, and a full-screen
  player keeps Instagram's navigation bar tappable, so you can leave the way you normally would.
- **Back out** (YouTube's default) — Shorts is a whole tab, so the app steps back out of it and
  lands you on the tab you were on before. If the app keeps re-opening it, the guard stops fighting
  and leaves the app.

You can swap the style per app.

### The rest of the feed guard

- **Your own messages.** Every line the app shows you is text you wrote. The built-in ones are a
  starting point and can all be deleted. Choose whether they cycle in order, appear at random, or
  stay fixed.
- **Anti-scroll reminders.** Notices when scrolling has stopped being deliberate and shows one of
  your messages. Three sensitivities, then it stays quiet for a while so it never becomes wallpaper.
- **Scheduled breaks.** A window where blocking pauses on purpose — a Sunday evening allowance, say
  — which re-locks itself afterwards.

---

## Adult content filter

Optional, off until you switch it on.

- **Works in every browser**, Chrome and Opera included, with no root. It filters DNS on the device
  through a local VPN, which is the only hook that sits underneath a browser.
- **Your own list.** Add domains by hand, or import a list file from your phone's storage. Adding
  `example.com` also covers every subdomain; an allowlist entry beats a broader block, so a wrongly
  blocked site can be let through without editing anything else.
- **Closes the DNS-over-HTTPS bypass**, and checks the address bar as a second layer for browsers
  that ship a hard-coded resolver.
- **Nothing is fetched and nothing is reported.** The bundled list is inside the app. A blocked name
  is answered on the device and never leaves it. Allowed names go to whichever resolver your network
  already gave the phone.

### Turning it off takes 48 hours

Switching it **on** is instant. Switching it **off** is not:

1. Ask to turn it off. A 48 hour wait starts, and the filter keeps running throughout.
2. When the wait is served, solve a set of arithmetic problems in a row. A wrong answer throws away
   the whole set and starts a short penalty, so guessing is worse than working them out. The problems
   are generated from a stored seed, so closing the app cannot reroll a hard one into an easy one.
3. Only then does the filter stop.

Cancelling is one tap at any point, and turning the filter back on is instant. The friction only
ever points one way.

**The wait cannot be skipped by changing the clock.** Progress is credited against the phone's
monotonic uptime, which no setting can move. Time while the phone is powered off is only witnessed
by the calendar, so it is credited but capped per restart, and any disagreement between the two
clocks is recorded, shown to you, and adds an hour to the requirement.

---

## What stays on the device

- No account, no backend, no analytics, no crash reporting, no advertising ID.
- Settings, messages, blocklist and cooldown state are files in the app's private storage, excluded
  from cloud backup and device transfer — a backup would also be a way to restore a "filter off"
  state onto a new phone.
- The screens the guard reads are turned into a list of view names, scored, used for one decision,
  and dropped. Nothing about them is written down.
- `INTERNET` permission exists for exactly one reason: forwarding, while the filter is on, the DNS
  questions your phone already asks. The app makes no requests of its own, and plaintext HTTP is
  denied outright in its network config.

## What it cannot do

Worth being straight about, because a tool like this is friction rather than a cage:

- Anyone holding the unlocked phone can uninstall the app or revoke its accessibility permission.
- A browser with a hard-coded DNS-over-HTTPS server can route around the DNS filter. That is why the
  address-bar check exists — but it is a second layer, not a guarantee.
- Android allows one VPN at a time, so another VPN app replaces this one.
- Apps rename their internal views between releases. If a feed stops being recognised, the detection
  signatures are data rather than code and can be replaced without a new build.

---

## Building it yourself

Needs JDK 17 and the Android SDK (API 35).

```bash
./gradlew :core:test        # the decision logic, 75 tests, no device needed
./gradlew :app:assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

CI runs both on every push and attaches the APK to a release.

### How it is laid out

```
core/   pure Kotlin, no Android at all — every decision the app makes
  detect/     screen snapshots, signature matching, "where did this reel come from"
  policy/     block or allow, and the invariant that only short video is blockable
  blocklist/  domain matching (suffix trie), list parsing, ruleset assembly
  dns/        DNS and IPv4/IPv6 UDP wire formats, the filter engine
  lock/       48 hour cooldown, tamper-resistant clock, challenge generation
  scroll/     anti-scroll detection
  schedule/   break windows

app/    the Android half — thin on purpose
  service/    accessibility service, overlays, address-bar guard
  vpn/        the local DNS tunnel
  data/       DataStore repositories
  ui/         Compose screens
```

Keeping the logic in a plain Kotlin module is what makes it testable: the DNS packet encoder, the
clock-tampering rules and the "is this a DM reel or the Reels tab" question are all exercised
against fixtures rather than against a phone.

---

## Licence

CC0 1.0 Universal — see [LICENSE](LICENSE).
