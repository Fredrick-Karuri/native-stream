## Architectural Spec: "NativeStream Connect" Session Sync
This specification outlines a lightweight, state-driven synchronization system that allows mobile clients to discover and hand off what is currently playing on the macOS client over a Local Area Network (LAN).
------------------------------
## 1. System Topology
The architecture uses a centralized state pattern. The local media backend server acts as the single source of truth for the active playback session, while the client apps function as stateless consumers.

┌─────────────────┐       HTTP PUT (State Update)       ┌────────────────────────┐
│  macOS Client   │ ──────────────────────────────────> │ Local Backend Server   │
└─────────────────┘                                     │ (Binding: 0.0.0.0:xxx) │
                                                        └───────────┬────────────┘
                                                                    │
┌─────────────────┐       HTTP GET (State Fetch)                    │
│  Mobile Client  │ <───────────────────────────────────────────────┘
│ (iOS / Android) │
└─────────────────┘

------------------------------
## 2. Backend Server Specifications## Network Binding

* The server must bind to 0.0.0.0 on a designated port. This exposes the API interface across the entire local subnet, allowing mobile devices connected to the same Wi-Fi network to communicate with it. [1] 

## State Storage

* Volatile In-Memory Storage: The active playback session does not require database persistence. It is stored as a simple, volatile global object or dictionary in memory. If the server restarts, the state gracefully defaults to null.

## API Endpoints

   1. PUT /api/session (State Registration)
   * Inbound Call From: macOS Client.
      * Trigger: Dispatched immediately when a user opens a live stream channel.
      * Payload Structure: A flat JSON string containing the active channel's identification code (e.g., activeChannelId: "sky-sports-1"). Passing a null value clears the active session when playback is manually stopped.
   2. GET /api/session (State Synchronization)
   * Inbound Call From: Mobile Clients (iOS & Android).
      * Trigger: Dispatched during app launch, view appearance hooks, or foreground state transitions.
      * Response Payload: Returns the active channel JSON structure. If no stream is active on the network, it returns an empty payload or an explicit null string.
   
------------------------------
## 3. macOS Client Specifications (The Publisher)## Lifecycle Events

* Stream Start: Upon successful hardware HLS decoding initialization, the Mac client triggers a background network request executing the PUT /api/session contract, passing the unique channel string.
* Stream Switch: If the user switches channels, a new PUT payload immediately overwrites the existing server session state variable.
* App Exit / Stream Close: When the player window closes or the application terminates, the Mac client fires a final clearing payload (activeChannelId: null) to prevent dead-state references on the mobile client.

------------------------------
## 4. Mobile Client Specifications (The Consumer)## Data Layer Strategy

* The mobile application's data management view-model maintains a dedicated state variable (e.g., remoteActiveChannel).
* When fetching the session payload, the mobile client executes a string-matching sequence against its locally stored channel array (channel.id == response.activeChannelId). If a match is found, the reference is cached locally; otherwise, it resolves to nil.

## Lifecycle Triggers (Non-Polling Strategy)
To keep memory usage and network chatter near zero, avoid continuous background polling loops. Instead, fetch the data state during explicit user interaction boundaries:

   1. App Foregrounding: Fetch when the mobile app switches from suspended background status to the active foreground.
   2. View Mounting: Fetch whenever the user navigates directly onto the primary Home feed screen.

## UX Execution Layer

* Conditional UI Injection: The mobile Home screen layout injects a specialized, high-priority "Handoff Banner" component at the absolute top of the scroll content view only when remoteActiveChannel is non-nil.
* Direct Interaction Routing: Tapping this banner bypasses standard categorization layers entirely. It instantly resolves the matched channel's streaming source URL and pushes the full-screen landscape media player activity directly over the current UI stack.