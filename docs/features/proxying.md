
------------------------------
## Feature Definition: Silent Geo-Block Failover Routing

## 1. Executive Summary
* This feature enables the IPTV/M3U streaming application to automatically and silently bypass regional geographic restrictions (geo-blocks) on third-party video streams. The system initially attempts a zero-cost direct connection. If a geo-restriction error is intercepted, it dynamically re-routes the stream through a regional proxy fleet via the Go backend without disrupting the client playback experience.
------------------------------
## 2. User Stories

## User Story 1: End-User Stream Playback

* As an IPTV App User,
* I want to click on any channel in my custom M3U playlist and have it play seamlessly,
* So that I don't have to manually figure out which channels are geo-restricted or configure external VPN tools.

## Acceptance Criteria (User Story 1):

* Given a user selects a geo-blocked channel (e.g., Xumo US-only), the stream must load and play within a maximum buffer timeout of 1.5 seconds.
* The user must never see raw HTTP status errors (like 403 Forbidden or 451 Unavailable).
* The stream must switch to the proxy path silently, without stuttering or stopping the native player UI (ExoPlayer/AVPlayer).

## User Story 2: Independent App Developer (Cost & Maintenance)

* As the System Architect/Developer,
* I want to route traffic through regional cloud proxies only when a stream is explicitly confirmed to be geo-blocked,
* So that I minimize outbound cloud bandwidth usage and keep my operational costs at exactly zero using my Azure Student allowances.

## Acceptance Criteria (User Story 2):

* Unrestricted streams must bypass the proxy network entirely ($0 bandwidth cost).
* The Go backend must abstract all proxy authentication and routing variables away from the Swift and Kotlin client builds.

------------------------------
## 3. Technical Specifications & Architecture

## A. The Endpoint Schema
The Go backend will expose a unified proxy routing endpoint. All client applications will hit this interface.

* Endpoint: GET /v1/stream/proxy
* Query Parameters:
* url (string, URL-encoded): The target streaming manifest destination.
   * expected_region (string, optional): A fallback ISO country code (e.g., US, UK).

## B. Sequence Diagram (The Operational Lifecycle)

```
[Kotlin/Swift Client]         [Go Backend Router]         [Azure Regional Proxy]       [Target Stream CDN]
         │                             │                            │                           │
         │─── 1. Play channel ────────►│                            │                           │
         │    (Proxy Wrapper URL)      │─── 2. Direct HTTP GET ────────────────────────────────►│
         │                             │                                                        │ [GEO-BLOCKED!]
         │                             │◄── 3. Returns HTTP 403 / 451 ──────────────────────────│
         │                             │
         │                             │─── 4. Re-route Request ───►│                           │
         │                             │    (With Proxy Transport)  │─── 5. Spoofed GET ───────►│
         │                             │                            │                           │ [ACCEPTED]
         │                             │                            │◄── 6. Stream Manifest ────│
         │                             │◄── 7. Pipe Data Stream ────│
         │◄── 8. Playback Initiated ───│

```
------------------------------
## 4. Platform Implementation Requirements

## 💻 Go Backend Responsibilities

   1. M3U Playlist Parser Module: Intercept uploaded .m3u files. Parse and rewrite all stream target parameters to point directly to https://yourdomain.com{TARGET_URL}.
   2. Reactive Transport Engine: Implement an http.RoundTripper handler capable of trapping 403, 410, and 451 status codes. On trap execution, immediately instantiate an authenticated upstream connection via the Azure VM Proxy Pool.
   3. HLS Manifest Rewriter (Crucial): If the proxy returns a text/playlist structure (.m3u8), loop line-by-line. Any relative or direct paths inside the manifest file pointing to .ts / .m4s video segments must be appended with the backend proxy prefix before being sent down to the client.

## 🍏 Mac Client (Swift / AVFoundation) Responsibilities

   1. Initialize standard AVPlayerItem using the backend-mutated proxy tracking string.
   2. Set connection timeout properties inside the network configuration to match the 1.5-second failover budget window.

## 🤖 Android Client (Kotlin / Media3) Responsibilities

   1. Construct an ordinary HlsMediaSource injecting standard OkHttpDataSource.Factory.
   2. Ensure that the client passes a descriptive User-Agent header so the Go backend can log whether errors originate from device network profiles vs. server-side blocks.

------------------------------
## 5. Security & Edge Case Parameters

* Proxy Leakage Protection: Under no circumstances should the backend bubble up your private Azure Proxy server IP addresses to the client consoles or logs.
* Infinite Loop Safety Catch: If a stream is legitimately broken or offline (returns a 403 or 404 universally from all global endpoints), the Go server must terminate the request loop after one failed retry and return an explicit HTTP 502 Bad Gateway to prevent crashing your proxy servers with infinite connection loops.
