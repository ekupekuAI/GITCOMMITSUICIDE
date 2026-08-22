# RescueMesh Final Protobuf Schema

**Status:** AUTHORITATIVE.
**Date:** 2026-08-22
**Scope:** Wire schema for the Android-only offline emergency mesh prototype.

This document freezes the protobuf contract used by `docs/FINAL_ARCHITECTURE.md`.
The implementation schema lives at:

```text
app/src/main/proto/rescuemesh/v1/mesh.proto
```

## Package

```proto
syntax = "proto3";

package rescuemesh.v1;

option java_package = "com.rescuemesh.app.protocol";
option java_multiple_files = true;
```

## MeshMessage

`MeshMessage` is the only data payload relayed through DATA frames.

Fields:

| Field | Type | Number | Notes |
|---|---|---:|---|
| `message_id` | `bytes` | 1 | Required by app validation. Exactly 16 bytes. |
| `origin_node_id` | `bytes` | 2 | Required. Exactly 16 bytes. |
| `destination_id` | `bytes` | 3 | Empty means broadcast SOS. Non-empty must be 16 bytes. |
| `message_type` | `MessageType` | 4 | Required semantic type. |
| `priority` | `uint32` | 5 | Lower value is higher priority. SOS default is 0. |
| `ttl` | `uint32` | 6 | Remaining relay budget. Decremented only after successful relay acceptance. |
| `hop_count` | `uint32` | 7 | Incremented only by the accepting receiver after successful forwarding. |
| `created_at_ms` | `uint64` | 8 | Sender wall-clock epoch milliseconds. |
| `expires_at_ms` | `uint64` | 9 | Absolute expiry wall-clock epoch milliseconds. |
| `payload_version` | `uint32` | 10 | Payload schema version. MVP default is 1. |
| `payload` | `bytes` | 11 | UTF-8 text payload for MVP SOS/text messages. |
| `signature` | `bytes` | 12 | Reserved. Empty means unsigned in MVP. |

## ControlMessage

`ControlMessage` is used only in CONTROL frames and notification responses.

Message kinds:

| Kind | Purpose |
|---|---|
| `HELLO` | Exchange node ID, protocol version, and capabilities. |
| `INVENTORY_REQUEST` | Ask peer which stored messages it needs. |
| `INVENTORY_RESPONSE` | Return known message IDs so duplicates are not sent. |
| `ACK` | Confirm durable Room persistence of a received message. |

## Validation Rules

- All canonical IDs are raw 16-byte values, never string IDs.
- Serialized frame payloads larger than 500 bytes are rejected by the BLE framer.
- The app targets serialized `MeshMessage` values of 250 bytes or less.
- Unknown enum values are rejected at the mesh validation layer.
- A message with `ttl == 0` is not accepted for relaying.
