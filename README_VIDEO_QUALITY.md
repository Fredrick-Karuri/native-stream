# Video Quality

NativeStream lets you cap the maximum video quality for all streams, with a per-session override directly in the player. This prevents adaptive bitrate from thrashing on slow connections and gives you predictable bandwidth use without sacrificing the ability to go high-quality when you want it.

## What You Can Do

- Set a persistent quality cap (Auto / 480p / 720p / 1080p) that applies to every stream you watch
- Override quality for the current session by tapping the quality badge in the player — resets automatically when you switch channels
- See the currently detected stream quality at a glance in the player top bar
- Changes take effect immediately mid-stream — no need to restart playback

## Quick Start

1. Open **Settings → Playback**
2. Tap a quality option in the **Video quality** picker
3. Start watching — the cap is applied automatically

To override for the current session only, tap the quality badge (e.g. **FHD**, **HD**) in the top-right of the player while something is playing. Tap again to cycle through options.

## Quality Levels

| Label | Cap | Best for |
|-------|-----|----------|
| Auto  | None (adaptive) | Fast connections, let ExoPlayer decide |
| 480p  | 1.5 Mbps | Mobile data or congested networks |
| 720p  | 4 Mbps | Balanced — good quality, moderate bandwidth |
| 1080p | 8 Mbps | Home Wi-Fi, high-quality viewing |

## Requirements

- Android client with ExoPlayer (Media3)
- No server-side changes required — quality capping is client-only