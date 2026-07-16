# Troubleshooting

## Server unreachable

**Symptom:** Mac or Android app can't connect on launch.

Ensure NativeStream Server is running and reachable:

```bash
curl http://localhost:8888/api/health
```

Both clients scan for the server automatically via mDNS on launch (`_nativestream._tcp`). If discovery fails — common on corporate or guest Wi-Fi that blocks mDNS — enter the server URL manually:

- Mac: Settings → Server
- Android: Settings → Server, or during onboarding

## No content available

Verify content sources have been configured and synchronized. Check discovery status:

```bash
curl http://localhost:8888/api/discovery/status
```

If `discovery_enabled: false` in `config.yaml`, no new channels will be found automatically — see [configuration.md](configuration.md#option-reference).

## Playback issues

Check your network connection, or adjust playback settings (buffer preset) for improved stability. Both clients retry a failed stream up to 3 times with a 2s delay before showing an error overlay — see [architecture.md — Client Playback](architecture.md#client-playback).

If a specific stream is consistently unhealthy, trigger a manual re-probe:

```bash
curl -X POST http://localhost:8888/api/probe
```

## Missing schedule information

Verify EPG data is available for the selected content:

```bash
curl http://localhost:8888/epg.xml
```

If `football_data_key` is unset in `config.yaml`, only ESPN-sourced schedules will populate — see [configuration.md](configuration.md#option-reference).

## Local Media Connect not working

Ensure the server is running and both devices are on the same local network. The control service is advertised as `_nativestream-ctrl._tcp` — verify from the server machine:

```bash
dns-sd -B _nativestream-ctrl._tcp local
```

If mDNS discovery fails but the server is reachable, the connection session list is also available over plain HTTP as a fallback:

```bash
curl http://localhost:8888/api/sessions
```

See [local-media-connect.md](local-media-connect.md) for the full protocol and known risks (e.g. Android Doze/battery optimization killing the WebSocket session).

## Where to go deeper

- System-level overview: [architecture.md](architecture.md)
- Server internals and self-healing behavior: [server-architecture.md](server-architecture.md)
- Mac internals: [mac-architecture.md](mac-architecture.md)
- Full endpoint and WebSocket message reference: [api.md](api.md)
- Android-specific diagnostics: [android-architecture.md](android-architecture.md), [android-performance.md](android-performance.md)