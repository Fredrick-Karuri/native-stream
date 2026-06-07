# NativeStream — Onboarding Redesign Spec

**Goal:** Get the user to a populated Now screen in under 60 seconds.  
**Principle:** Reveal the magic before asking for commitment.

---

## The Problem with Current Onboarding

Three sequential steps before the user sees anything. Server → Playlist → EPG. It reads like a setup wizard, not a product. The user has no idea what they're getting until after they've configured it.

For a technically complex product (self-hosted server, M3U, XMLTV) the onboarding has to do two jobs simultaneously:

1. **Educate** — explain what NativeStream actually is and why it needs these things
2. **Reduce friction** — make the setup feel inevitable, not laborious

Right now it does neither well.

---

## The Mental Model: "Your TV, On Your Phone"

Every screen in the onboarding should communicate one thing:
**NativeStream connects your phone to your home streaming server so you can watch live TV — with a smart guide that knows what's on.**

Users don't need to understand M3U or XMLTV. They need to understand:
- They have a server
- This app talks to it
- Then they see live TV with a programme guide

---

## Proposed Flow

### Screen 0 — Splash / Value proposition (2–3 seconds, auto-advance)

Not a loading screen. A statement.

```
[Full screen, dark background]

  ▶  NativeStream

  Your live TV.
  On every screen.

  [subtle animation — channels populating, score ticking up]
```

Auto-advances after 2s OR on tap. No buttons needed.  
**Purpose:** prime the user's brain with what they're about to get.

---

### Screen 1 — "Where's your server?" (the only hard question)

```
[Icon: a small server/tower glyph]

  Connect to your server

  NativeStream works with your NativeStream Server
  running on your home network.

  [ http://192.168.1.42:8888        ] ← pre-filled with last-known or common default
  
  [Tip: Open Terminal and run `make run-server` if it's not running yet]

  [ Test Connection ]
```

On success → **immediately** show an inline celebration:

```
  ✓ Connected  ·  142 channels found  ·  EPG loaded
  
  [ Let's go →  ]
```

The key insight: **show them what was found.** "142 channels found" is the first moment the product feels real. The bulb starts to flicker.

On failure → inline diagnostic, not a dead end:

```
  ✗ Couldn't reach that address
  
  → Is the server running? Try: make run-server
  → On the same WiFi network?
  → Check the IP in your server's terminal output
  
  [ Try again ]   [ I need help ]
```

---

### Screen 2 — (conditional) "No EPG found — add a TV Guide?"

Only shown if the server's EPG endpoint returned no data.  
If EPG is healthy → **skip entirely**, go straight to Now screen.

```
[Icon: a TV guide / calendar glyph]

  Add a TV Guide  (optional)

  A TV Guide shows you what's on and upcoming match times.
  Your server might already have one — we didn't find it.

  [ http://192.168.1.42:8888/epg.xml   ]

  Or use a public guide:
  [ Use IPTV-org guide ]  ← pre-fills a working public EPG URL

  [ Skip for now ]   [ Add Guide → ]
```

**The "Use IPTV-org guide" button is the escape hatch.** Most users don't know where to get an EPG. One tap solves it.

---

### Screen 3 — The Aha (Now screen, live)

No "you're all set" screen. No confetti. Just — the Now screen, populated, real.

The first time they see live matches bucketed with scores, live on air, starting soon — **that's** the moment. The product speaks for itself.

If the Now screen is empty (no live content right now):

```
  [Empty state — but warm, not clinical]
  
  📺  Nothing live right now
  
  Next up: Arsenal vs Tottenham · 20:00
             Sky Sports
  
  Browse all 142 channels →
```

Even the empty state references their actual data — their channel count, their upcoming matches.

---

## Key Design Decisions

### 1. Auto-derive everything from server URL
The moment the connection test passes, silently try:
- `{serverUrl}/playlist.m3u` → playlist
- `{serverUrl}/epg.xml` → EPG

If both work → skip Screen 2 entirely. Zero configuration beyond the IP.

### 2. Show discovery in real time
During the connection test, show what's being found:

```
  Connecting…
  ✓ Server reached
  ✓ Playlist found — loading channels…
  ✓ TV Guide found — loading programme data…
```

This is not a progress bar. It's a narrative. The user understands what's happening.

### 3. The error states are teaching moments
Every error message answers: what went wrong, why, and what to do. Never a raw error code. Never "connection failed."

### 4. Pre-fill intelligently
- Default server URL: `http://192.168.1.42:8888` (most common home server IP pattern)
- If a previous partial setup was detected, restore it
- If mDNS discovery finds a server on the network, auto-fill (nice-to-have, AND-025 extension)

### 5. "I need help" is not failure
The help path should be warm:
```
  Need a hand?
  
  NativeStream works with a companion server app
  that runs on your Mac or home server.
  
  → Get NativeStream Server  [link]
  → Watch the setup guide    [link]
  → Join the community       [link]
```

This is also where new users who don't have a server yet land. It's acquisition, not support.

---

## What We Cut

| Removed | Reason |
|---|---|
| Step-by-step wizard chrome (step 1 of 3) | Implies complexity before the user experiences it |
| Separate "Add Playlist Source" step | Auto-derived from server URL |
| "You're all set!" completion screen | The Now screen IS the completion |
| Progress pills at the top | Implies a long journey; max 2 real screens |
| EPG step shown unconditionally | Only shown when genuinely needed |

---

## Acceptance Criteria

**Given** the server is healthy and has both playlist and EPG  
**When** the user enters the server URL and taps "Test Connection"  
**Then** they reach the populated Now screen having seen 1 input screen only

**Given** the server is healthy but has no EPG  
**When** connection test passes  
**Then** Screen 2 (EPG) is shown with public EPG escape hatch available

**Given** the connection test fails  
**When** the error is shown  
**Then** at least 2 specific actionable suggestions are visible; a retry is one tap away

**Given** the user has partially completed onboarding before  
**When** they reopen the app  
**Then** their server URL is pre-filled

**Given** the Now screen is empty (no live content)  
**When** onboarding completes  
**Then** the next upcoming programme from their actual data is shown in the empty state

---

## Implementation Notes

- Onboarding lives in `OnboardingScreen.kt` — replace 3-step flow with 2-screen max
- Connection test should call `/api/health`, `/playlist.m3u` (byte count only), and `/epg.xml` (byte count only) in parallel
- Results communicated via `StateFlow<OnboardingConnectionState>` enum: `Idle | Checking | Success(channels, hasEpg) | Failure(reason)`
- `hasEpg = false` in `Success` → show Screen 2; `hasEpg = true` → skip to Now
- mDNS auto-discover (nice-to-have): use `NsdManager` to find `_nativestream._tcp` service on LAN

---

## Ticket: AND-025-R (Onboarding Redesign)
- **Effort:** L
- **Needs:** This spec, `OnboardingScreen.kt`, `SettingsDataStore.kt`, `ApiClient.kt`
- **Done when:**
    - User with healthy server reaches Now screen in ≤ 2 taps
    - Error states show actionable copy, not raw errors
    - EPG screen skipped when server EPG is healthy
    - Server URL pre-filled on return visits
    - Onboarding completion rate measurably higher than 3-step flow (A/B if possible)