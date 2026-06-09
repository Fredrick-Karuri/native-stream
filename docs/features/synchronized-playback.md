## Product Feature Specification: Synchronized Playback Session (Pragmatic Whiteboard Model)

## 1. Core Intent

* To establish a single, central Playback Session hosted on the Go server that acts as a shared network whiteboard [INDEX]. Instead of routing blind, direct commands between applications, devices dynamically read from and write to this single state object using their unique hardware identity (device_id) [INDEX]. Playback transitions occur safely based on state compliance rather than forced network interrupts.
------------------------------
## 2. System Roles & Properties

## The Shared Session State (The Whiteboard)

* Definition: A persistent, single-instance memory bucket on the Go server tracking what is currently playing on the local network segment.
* Properties:
* owner_device_id: The ID of the specific device currently authorized to play audio and video data [INDEX].
   * channel_id & stream_url: The media routing hooks for the active broadcast feed [INDEX].
   * name: Display title of the channel.
   * position_ms: The playback time marker to preserve position during handoff events [INDEX].

## The Peer Clients (Peers)

* Definition: The Android and macOS application runtime instances, each injecting a hardcoded, unique string identifier (device_id) during network interactions.
* Role: Continuous observers. They inspect the whiteboard to see if ownership has been shifted to them, or if they need to surrender local playback because another device took over the track [INDEX].

------------------------------
## 3. Data Schema (GET/PUT /api/session)

{
  "owner_device_id": "mac_pro_fredrick_01",
  "channel_id": "99991247",
  "name": "Live Sports Channel",
  "stream_url": "http://10.142.15",
  "position_ms": 45000
}

------------------------------
## 4. The Pragmatic State Lifecycle Flow

   [ Device A (Android) ]            [ Go Server Whiteboard ]            [ Device B (Mac) ]
     (Playing Locally)                          │                                 │
             │                                  │                                 │
     (User Taps Handoff)                        │                                 │
             │                                  │                                 │
     (Updates Whiteboard) ─────────────────────►│                                 │
     Owner: "mac_pro_01"                        │                                 │
             │                                  │                                 │
             │                                  │◄─────── (Background Poll) ──────┤
             │                                  │                                 │
             │                                  │                        (Evaluates Context)
             │                                  │                        Matches My ID? (YES)
             │                                  │                                 │
             │                                  │                        1. Launches Player
             │                                  │                        2. Decodes StreamUrl
             │                                  │                                 │
             │◄─────── (Background Poll) ───────┤                                 │
             │                                  │                                 │
    Owner is STILL "mac_pro_01"?                │                                 │
             ▼                                  │                                 │
     1. Stop Local Playback                     │                                 │
     2. Render Placeholder UI                   │                                 │

## Phase 1: Relinquishing Ownership (Device A)

   1. While streaming, the user explicitly opts to push the session to the target screen.
   2. Device A updates the Go whiteboard (PUT /api/session) by writing the target machine's identifier into the owner_device_id field and passing the current position_ms timestamp [INDEX].
   3. Safety Rule: Device A does not stop rendering video yet [INDEX]. It continues playing normally while waiting for the secondary device to absorb the state change.

## Phase 2: Interception & Target Activation (Device B)

   1. Device B evaluates the whiteboard during its observation cycle.
   2. It detects that owner_device_id == "mac_pro_01", but its own internal video player engine is currently uninitialized.
   3. The Validation Gate: Device B checks if it is in a position to handle playback (e.g., app is awake, system has decoding availability).
   * If it cannot handle it: It ignores the entry completely. Device A simply keeps playing indefinitely.
      * If it can handle it: Device B immediately launches its media containers and resumes the stream_url string at the extracted position_ms offset [INDEX].
   
## Phase 3: Passive Session Tear-down (Device A)

   1. Device A hits its next polling tick and reads the whiteboard.
   2. It detects that owner_device_id is still set to "mac_pro_01" [INDEX].
   3. Because the state remains unchanged, Device A safely deduces that Device B successfully processed the handshake, read the whiteboard metrics, and assumed media rendering responsibility.
   4. Device A kills its hardware video decoder, releases the audio tracking channels, and gracefully switches its local layout interface to display the passive Apple-style placeholder text ("Playing on MacBook Pro").

------------------------------
## 5. Reverse Playback Reclamation ("Pull Back")
If the user touches the "Pull Back Here" interface component on Device A:

   1. Device A writes its own identifier back to the whiteboard (owner_device_id = "android_01") and triggers its player engine [INDEX].
   2. Device B catches the modification during its next network check, stops its desktop player, and seamlessly transitions back to an idle state window [INDEX].
