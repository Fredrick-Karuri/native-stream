# NativeStream — Product Thinking

*Running notes on positioning, aha moments, and roadmap direction.*

---

## Core Thesis

**"Point it at your server. See what's on."**

Everything flows from this. The user has already done the hard part — they have a server running, they have streams. Every other client they've tried is either a janky generic IPTV player with no EPG awareness, Mac-only, or requires port-forwarding and a cloud middleman.

NativeStream's moat is not the score overlay or the player. It's that it **understands** what's on — live matches, on-air, starting soon — from *their* EPG, on their local network, with zero fuss.

---

## The Aha Moment

The aha is the **Now screen populating with live matches and current programmes** for the first time.

Not the player. Not the score overlay (that's retention). The moment the user sees their content organised intelligently — bucketed, labelled, EPG-aware — is when they understand this isn't a generic M3U player. That's acquisition.

### Duolingo parallel
Duolingo doesn't make you configure anything. One language pick, one exercise, dopamine in under 60 seconds. The product reveals its value before asking for commitment.

Our equivalent: **one field (server IP), one tap, populated Now screen.** Auto-derive `playlist.m3u` and `epg.xml` from the server URL. Don't make the user think about it.

### Key metric
**Time from app open → populated Now screen.**

Current onboarding is 3 steps (server → playlist → EPG). Should feel like 1.

---

## The Streams Problem

Getting streams is hard — but that's not on us. The user has already solved this before they find NativeStream. We don't touch that problem and we don't need to.

What we do: make the experience of *using* those streams dramatically better than anything else available.

---

## Roadmap: VPN / Proxy Integration

### The insight
Some streams require a VPN. Users already know this — Nord and ExpressVPN have spent a billion dollars teaching them that VPN = access to content their location blocks. We inherit that mental model for free.

We never say "geoblocking." We say **security, privacy, reliability.** Same thing, better framing.

### Why it fits
We already have server-side proxy infrastructure for header injection (Referer, User-Agent). A VPN tunnel is the natural extension of that.

### The leverage point
**If the tunnel lives in the server, not the device:**
- Works across all clients (Android, Mac) automatically
- User configures once, all devices benefit
- Positions NativeStream Server as personal streaming infrastructure, not just a playlist server

### Implementation shape (future)
- Per-source or per-channel VPN profile in Settings
- WireGuard integration — cleanest option, Android has first-class `VpnService` API
- Server-side tunnel preferred over on-device (see leverage point above)
- UI: dead-simple toggle per source, same pattern as the proxy toggle already in Settings

### Framing for users
> *"Some streams work better with a secure connection. NativeStream handles it automatically."*

---

## What We Are Building

Not a player app. A **personal streaming infrastructure** — server + intelligent clients — that happens to be the best way to watch live sport on your own hardware.

The VPN thought crystallises this: if routing lives server-side, the client is just a beautiful window into your own infrastructure. That's a different product category from IPTV players, and a much more defensible one.

---

## Monetisation

### The starting insight
The user already pays for their streams — a provider, a subscription, something. NativeStream is the client layer on top of infrastructure they've already committed to. That changes everything about how we charge.

### Model: one-time purchase
**No subscription. Ever.**

Users in this space are allergic to recurring fees for software sitting on top of content they already pay for. A clean one-time price on the App Store / Play Store — pay once, own it. That's the anti-Netflix, anti-Jellyfin-donation-model statement and it will resonate immediately with this audience.

No ads. Full stop. One ad breaks the Jony Ive aesthetic and signals we don't trust our own product enough to charge for it.

### The Pro tier: VPN / tunnel integration
The natural paid unlock. Core app is the one-time purchase. The infrastructure features — per-channel WireGuard routing, VPN profiles, server-side tunnel management — unlock as a one-time Pro upgrade.

This works because:
- It's genuinely more valuable (solves the stream access problem)
- It's clearly separable from the core experience (core = watch; Pro = watch anything)
- It doesn't gate the aha moment behind a paywall
- Server-side tunneling has real infrastructure cost justification if we ever host anything

### Pricing instinct

| Tier | Price | What you get |
|---|---|---|
| Core | $4.99 – $9.99 one-time | Full app, all clients, all EPG features |
| Pro | $14.99 – $19.99 one-time | Everything + VPN / tunnel integration |

Numbers to validate with the market — but the structure is right.