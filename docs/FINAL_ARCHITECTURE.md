# RescueMesh — Final Architecture (Single Source of Truth)

**Status:** AUTHORITATIVE — supersedes all other documents where they conflict.
**Date:** 2026-08-22
**Scope:** Android-only offline emergency mesh prototype.

---

## 0. Precedence rules for this project

1. **This document** (`docs/FINAL_ARCHITECTURE.md`) and `docs/FINAL_PROTO.md` are the only implementation references.
2. `emergency_mesh_technical_architecture_v0_1.md` is the primary research input; concepts are inherited, conflicting details are replaced by this document.
3. `ANDROID_BLE_RESEARCH.md` is BLE platform guidance; where its GATT/packet details differ from here, **this document wins**.
4. `EMERGENCY_MESH_UI_SPEC.md` and `Dashboard.md` are UX *intent* only. Any UI element whose data is not produced by the real protocol is forbidden (see §11).
5. Root `README.md` belongs to an unrelated project (web stack, Supabase/Vercel/AI-tool notes). It is **ignored completely** and must not influence any implementation decision.

---

## 1. System definition

RescueMesh is an **application-layer opportunistic store-carry-forward emergency mesh over Android Bluetooth Low Energy**.

- Devices discover nearby participating devices via simultaneous BLE advertise + scan.
- Messages are small protobuf frames relayed over GATT.
- Every node persists every accepted message in Room/SQLite **before** acknowledging receipt or attempting further forwarding.
- Duplicate suppression is database-enforced via immutable `message_id`.
- Propagation is bounded by hop TTL **and** absolute expiry.
- No internet, cellular, cloud database, Firebase, Supabase, or external API is required for any core function.

It is **not** an IP mesh router, not Bluetooth Mesh, and not a guaranteed-delivery system.

---

## 2. Locked technology stack

| Layer | Choice |
|---|---|
| Language | Kotlin only |
| UI | Jetpack Compose + Material 3, single-Activity |
| Async | Kotlin Coroutines + Flow |
| Serialization | Protocol Buffers (`protobuf-javalite`, protoc via Gradle plugin) — see `docs/FINAL_PROTO.md` |
| Persistence | Room 2.6.x over SQLite (KSP) |
| Transport | Android BLE only (`android.bluetooth.le` + GATT client/server) |
| DI | None (manual construction in an `AppContainer`) — no unnecessary dependencies |
| Networking | None. Zero HTTP clients in the APK |

Build toolchain (locked):

| Item | Value |
|---|---|
| Gradle | 8.7+ (wrapper committed) |
| Android Gradle Plugin | 8.5.x |
| Kotlin | 2.0.x |
| compileSdk / targetSdk | 35 |
| minSdk | 26 |
| JDK | 17 |

minSdk 26 rationale: covers the demo phone pool, includes stable `BluetoothLeAdvertiser`/legacy-advertising behavior, avoids multi-SDK legacy paths beyond the permission split below.

---

## 3. Module & package layout

Single Gradle module `:app` for the hackathon MVP, strictly layered by package so extraction into Gradle modules later is mechanical:

```text
com.rescuemesh.app
├── ble/        BleScanner, BleAdvertiser, GattServer, GattClient,
│               BleConnection, BleOperationQueue, BleFramer
├── mesh/       MeshCoordinator, MessageRouter, NeighborTable,
│               DedupIndex (Room-backed), ForwardingEngine
├── data/       AppDatabase, MessageEntity, ReceiptEntity, NeighborEntity,
│               AttemptEntity, DAOs, MessageRepository
├── protocol/   generated protobuf (rescuemesh/v1/*.proto), Wire.Frame codecs
├── identity/   NodeIdentityProvider (random 128-bit id, persisted)
├── service/    MeshForegroundService (phase ≥ 10, optional)
└── ui/         dashboard/, sos/, mesh/, alerts/, components/
```

Hard dependency-direction rule (inherited invariant):

```text
ui → mesh → ble
       ↓
      data (Room)      protocol (protobuf) used by mesh + ble only
```

- BLE code contains **zero** mesh-routing logic. It emits events only.
- Routing code never touches `Bluetooth*` classes directly.
- UI never touches BLE or SQL directly; it observes repository Flows.

---

## 4. BLE transport design (final)

### 4.1 Roles

Every node runs **both roles concurrently**, for the entire session:

- Peripheral: advertises the mesh service, hosts the GATT server.
- Central: scans for peers, connects out as GATT client.

### 4.2 Advertisement content (final)

Main advertisement packet (≤31 bytes):

```text
Flags (3 B) + Incomplete list of 128-bit service UUIDs (18 B) = MESH_SERVICE_UUID
```

Scan response (optional): short local name `"RMESH"`.
No message data, coordinates, or battery ever appear in advertisements.

Scanning uses `ScanFilter(setServiceUuid(MESH_SERVICE_UUID))` — never unfiltered scanning.

### 4.3 GATT service layout (final — resolves the RX/TX-vs-Control/Data conflict)

One custom service, three characteristics:

```text
MESH_SERVICE  (readable, no encryption required for MVP)
│
├── CHAR_CONTROL   WRITE_NO_RESPONSE          client → server
│                  small control protobufs: HELLO, INVENTORY_REQUEST,
│                  INVENTORY_RESPONSE, ACK
│
├── CHAR_DATA_RX   WRITE_NO_RESPONSE          client → server
│                  framed MeshMessage payloads
│
└── CHAR_DATA_TX   NOTIFY (+ CCCD)            server → client
                   framed control responses AND framed MeshMessage payloads
```

Decision record:

- The research document’s `RX`/`TX` naming is ambiguous (each side calls its own link “TX”), and its separate `NodeInfo` read characteristic duplicates what `HELLO` already carries. **Dropped.**
- The architecture document’s logical split (control vs data) is kept, expressed as three concrete characteristics above.
- All server→client traffic (control replies and data) multiplexes on `CHAR_DATA_TX` notifications, distinguished by the frame type byte. This keeps the table minimal and avoids a fourth characteristic.
- `WRITE_NO_RESPONSE` preferred for throughput; reliability comes from the application-level ACK/inventory exchange, not from GATT write-with-response.

UUIDs (fixed constants, to be sanity-checked for collisions before demo):

```text
MESH_SERVICE   : 8E400001-F315-4F60-9FB8-838830DAEA50
CHAR_CONTROL   : 8E400002-F315-4F60-9FB8-838830DAEA50
CHAR_DATA_RX   : 8E400003-F315-4F60-9FB8-838830DAEA50
CHAR_DATA_TX   : 8E400004-F315-4F60-9FB8-838830DAEA50
```

### 4.4 Wire framing (transport layer owns framing)

Every characteristic value is a frame:

```text
offset  size  field
0       1     protocol_version   (= 0x01)
1       1     frame_type         (CONTROL=0x01, DATA=0x02)
2       2     payload_length     little-endian uint16
4       n     payload            serialized protobuf
```

Safeguards: reject `payload_length > 500`, reject unknown `protocol_version`, reject truncated frames, cap reassembly buffers. A serialized `MeshMessage` targets ≤ 250 bytes and therefore normally fits a single notification after MTU negotiation (target 247). **Fragmentation is out of scope for the MVP**; oversize payloads are rejected at creation time.

### 4.5 GATT operation discipline

One outstanding GATT operation per connection at a time, enforced by `BleOperationQueue`:

```text
CONNECT → discoverServices → enableNotifications(TX) → write… → (callback) → next
```

On any failure/status ≠ SUCCESS (including the generic GATT 133 family): `gatt.close()`, clear state, mark attempt failed, apply backoff. No logic keys on specific error codes.

### 4.6 Who connects to whom (collision avoidance)

Both nodes seeing each other must not simultaneously connect outward. Deterministic tie-break:

> The node with the lexicographically smaller `node_id` (byte order) initiates the GATT connection; the larger-id node waits passively as server.

If the initiator fails repeatedly, after backoff exhaustion roles may invert once as recovery.

---

## 5. Node discovery & liveness (final)

- Advertising and scanning run **simultaneously and continuously** while the app session is active (foreground, screen on — see §12 limitations).
- Every scan hit updates the neighbor observation: `node_id` (resolved during the subsequent HELLO), RSSI, `last_seen_at_ms`.
- Liveness classification is presentation-layer only:

```text
last seen ≤ 5 s   ACTIVE
5 s … 15 s        STALE
> 15 s            LOST
```

Thresholds are tunable policy, not platform guarantees. A LOST node remains visible in the UI marked LOST; nothing is deleted automatically except by retention policy.

---

## 6. Session & transfer protocol (final)

Per established GATT session:

```text
connect (tie-break initiator)
 → discoverServices
 → subscribe CHAR_DATA_TX notifications
 → HELLO exchange            (node_id, protocol_version, capabilities)
 → version gate              (mismatch ⇒ disconnect cleanly)
 → INVENTORY_REQUEST/RESPONSE (message_ids the sender holds, receiver lacks)
 → DATA frames for selected messages
 → receiver validates → persists → replies ACK per message
 → sender marks hop delivered; loop ends on idle or disconnect
```

Transfer atomicity invariant: **ACK is sent only after durable Room insert commits.** Crash-before-ACK ⇒ sender retries later; crash-after-ACK ⇒ message is already durable. No loss window.

---

## 7. Identity (final)

- `node_id`: **16 random bytes** (`UUID.randomUUID()` collapsed to 16 bytes or `SecureRandom`), generated at first launch.
- Persisted immediately (SharedPreferences + mirrored into Room `node_identity`).
- Display form: uppercase hex of first 4 bytes, e.g. `NODE-3FA91C02`. Full 16-byte form is canonical everywhere else.
- **Never** derived from MAC address (privacy + address randomization make it useless anyway).
- Same representation for `message_id` (16 random bytes per message) and `destination_id`.

All IDs are `bytes` on the wire, `BLOB` in SQLite, `ByteArray` in Kotlin. There is no string-ID variant anywhere in the codebase.

---

## 8. Message lifecycle & routing (final)

States (Room `messages.state`):

```text
CREATED → PERSISTED → QUEUED ⇄ WAITING_FOR_NEIGHBOR
                     → FORWARDING → RELAYED
DESTINATION-side: DELIVERED
Terminal: EXPIRED, REJECTED
```

Rules (all inherited invariants, restated as law):

1. Persist **before** first transmission attempt.
2. `message_id` is immutable; TTL/hop_count are the only mutable header fields.
3. **TTL decreases only on successful acceptance by a relay**: receiver applies `ttl' = ttl − 1` to its stored copy as part of the persist transaction, after validation. A failed connection attempt changes nothing on either side.
4. **hop_count increases only on successful forwarding**: the receiving node stores `hop' = hop + 1`; senders never mutate their own copies’ hop_count.
5. Received message with `ttl == 0` is not accepted for relaying (logged EXPIRED/REJECTED).
6. Absolute expiry (`expires_at_ms`) independently blocks forwarding regardless of remaining TTL. Default lifetime: 30 min.
7. Defaults: `TTL = 8`, broadcast `destination_id` empty for SOS, priority: SOS=0 (highest), ACK=1, text=2, control=3.
8. A disconnected peer never deletes a queued message. Store-carry-forward continues until delivered/expired/cancelled.
9. Queue bound: soft cap 200 messages; eviction order: expired → lowest priority → oldest non-SOS. Drops are recorded in `forward_attempts`/logs, never silent.

Routing is **local utility scoring**, not global pathfinding:

```text
score(peer, msg) = w1·recency(lastSeen) + w2·linkSuccessRate
                 − penalty(alreadyHasMessage via inventory) − penalty(backoffActive)
```

Weights start equal; tuned empirically. “Dynamic rerouting” = recomputing this candidate set from live observations whenever a forward is attempted; there is no route table to invalidate.

---

## 9. Deduplication (final)

Source of truth: **Room, not memory.**

- `message_receipts` table with `message_id BLOB PRIMARY KEY`.
- Receive path runs in one transaction:

```sql
INSERT OR IGNORE INTO message_receipts(...)  -- returns count
```

- `rows == 0` ⇒ duplicate ⇒ emit `DUPLICATE_REJECTED` event, do not forward, do not store a second message row.
- `rows == 1` ⇒ new ⇒ validate → insert `messages` row → apply TTL/hop rules → enqueue forward.
- Optional small in-memory LRU fronts the DB purely for speed; correctness never depends on it.
- Inventory exchange additionally prevents re-transmitting messages a peer already holds, reducing duplicate arrivals rather than merely absorbing them.

TTL bounds loops; message_id bounds repeated processing. Both are mandatory.

---

## 10. Persistence model (final, matches FINAL_PROTO 1:1)

```text
node_identity   (node_id PK BLOB, created_at_ms, key_version)
messages        (message_id PK BLOB, origin_node_id BLOB, destination_id BLOB NULL=broadcast,
                 type INT, priority INT, ttl INT, hop_count INT,
                 created_at_ms INT, expires_at_ms INT, payload_version INT,
                 payload BLOB, signature BLOB NULL, state TEXT,
                 received_at_ms INT, last_forwarded_at_ms INT NULL)
message_receipts(message_id PK BLOB, first_seen_at_ms INT, source_node_id BLOB, processed INT)
neighbors       (node_id PK BLOB, last_seen_at_ms INT, last_rssi_dbm INT,
                 state TEXT, protocol_version INT, last_connected_at_ms INT NULL,
                 connect_successes INT, connect_failures INT)
forward_attempts(auto-id, message_id, peer_node_id, started_at_ms, result TEXT, reason TEXT)
```

Indexes: `messages(state, expires_at_ms)`, `messages(priority)`, `forward_attempts(message_id)`.

---

## 11. UI truth policy (final — resolves spec contradictions)

Displayed fields are **only** those producible by the real protocol:

Allowed: node short ID · connection state (DISCOVERED/CONNECTED/LOST) · RSSI (dBm + Strong/Medium/Weak band) · last-seen age · hop count · TTL remaining · message status · relay participation · own battery (local device only) · measured latency where genuinely sampled (§11.1).

Forbidden unless the protocol later adds real support: peer distance in meters (RSSI is not distance), **peer battery percentage** (no such wire field exists — dropped from all node cards), fabricated network-health percentages, invented topology edges, example latency numbers presented as measurements, simulated packet animations.

Topology view draws an edge **only** for an actual GATT session or explicit forwarding event. Discovery alone renders a dotted “nearby” marker, never a line.

### 11.1 Latency measurement (real, not illustrative)

- Per-link latency: monotonic-clock RTT of DATA→ACK round trip ÷ 2, sampled per transfer, shown with sample count.
- End-to-end latency: `local_receive_time − created_at_ms` is **not trustworthy across phones** (unsynchronized clocks); it may be shown only labeled “clock-dependent”. Demo narrative relies on per-hop samples and event ordering, matching Dashboard.md’s honesty requirements.

---

## 12. Execution environment (final position)

MVP assumes **foreground app, screen on** for reliable scanning/advertising. A foreground service (`MeshForegroundService`) is a post-MVP enhancement, not a dependency of the core demo. Background/Doze/OEM-killer behavior is documented under KNOWN_LIMITATIONS, not promised.

Permissions (manifest, final):

```xml
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
                 android:usesPermissionFlags="neverForLocation"/>
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE"/>
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT"/>
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30"/>
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30"/>
<uses-feature android:name="android.hardware.bluetooth_le" android:required="true"/>
```

Runtime requests: `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT` (API 31+) or legacy pair (≤30). `neverForLocation` is valid because we derive no location from scans; **GPS SOS coordinates are out of MVP scope** (payload is free text typed by the user), so no location permissions are requested. Each permission denial degrades gracefully to a diagnostics screen; the app never crashes.

---

## 13. Security posture (final)

- `signature` field exists in the schema and is validated as “empty = unsigned” in MVP.
- Replay resistance today: unique message IDs + persistent receipts + expiry. Tamper/eavesdrop/spoof defenses are **explicitly deferred** (Phase 12+ backlog; AEAD + node keypairs, standard JCE primitives only).
- Input hardening IS in MVP scope: size caps, length validation, malformed-frame rejection without crashing, per-peer rate limiting of accepts (simple token bucket).

---

## 14. Event stream (for UI + dashboard + tests)

MeshCoordinator exposes a single `Flow<MeshEvent>` consumed by UI/dashboard/logging:

```text
DISCOVERED(peer, rssi) · CONNECTED(peer) · DISCONNECTED(peer, reason)
MESSAGE_CREATED(id) · RECEIVED(id, from) · FORWARDED(id, to, hop)
DUPLICATE_REJECTED(id, from) · ROUTE_FAILED(id, peer, reason)
ROUTE_CHANGED(id, newPeer) · DESTINATION_RECEIVED(id) · ACK_RECEIVED(id)
EXPIRED(id) · TRANSPORT_STATE_CHANGED(state)
```

Rule inherited from Dashboard.md: every visual animation maps 1:1 to an emitted event. No event ⇒ no animation.

---

## 15. Locked defaults table (ADR summary)

| Open question (arch doc §27) | Locked decision |
|---|---|
| min/target/compile SDK | 26 / 35 / 35 |
| Language | Kotlin only |
| Service/characteristic UUIDs | §4.3 constants |
| Characteristic layout | Control + DataRX(write) + DataTX(notify) |
| ID representation | 16-byte `bytes`/`BLOB`/`ByteArray`; hex short-form display |
| message_id | 16 random bytes |
| node identity | random 128-bit, persisted, non-MAC |
| Max serialized message | 250 B target / 500 B hard frame cap; fragmentation out of scope |
| Default TTL / expiry | 8 hops / 30 min |
| SOS addressing | broadcast (empty destination); addressed supported by schema |
| Signature | reserved field; crypto later phase |
| Concurrent GATT sessions | ≤ 3 (configurable) |
| Scan strategy | continuous filtered scan, foreground/screen-on MVP |
| Connection initiation | lower node_id initiates; role-invert after backoff |
| Background execution | foreground-first; service deferred |
| GPS/location | out of MVP scope |
