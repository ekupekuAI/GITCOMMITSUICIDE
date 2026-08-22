# RescueMesh — Android/BLE Research & Implementation Guide

**Role:** Android/BLE Research Engineer  
**Project:** Infrastructure-Independent Emergency Communication Mesh  
**Track:** Offline-First Systems  
**Primary stack:** Android + Kotlin + Bluetooth LE + Protocol Buffers + Room/SQLite

---

## 1. Purpose

This document converts Android BLE platform capabilities and constraints into implementation guidance for the RescueMesh lead developer.

The goal is **not** to build a generic Bluetooth tutorial. The goal is to answer:

> How can multiple Android phones discover each other, establish local BLE links, exchange small emergency packets, detect failed/moving relay nodes, reconnect, and support application-layer multi-hop forwarding without Internet, cellular service, or a cloud backend?

The hackathon Track 05 requires:

- no-connectivity operation using device-native capabilities such as Bluetooth LE/local compute;
- multi-node coordination and handling of node/path failure;
- bounded real-time response;
- handling of duplicates, conflicts and non-ideal input;
- a demonstration that includes failure/recovery rather than only the happy path.

---

# 2. Recommended Architecture

```text
                         RESCUEMESH APP
┌───────────────────────────────────────────────────────────┐
│                        UI / ViewModel                     │
├───────────────────────────────────────────────────────────┤
│                     Mesh Coordinator                      │
│                                                           │
│  Deduplication | TTL | Forwarding | Retry | Liveness     │
├───────────────────────────────────────────────────────────┤
│                     Local Repository                      │
│                       Room / SQLite                       │
├───────────────────────────────────────────────────────────┤
│                     BLE Transport                         │
│                                                           │
│ Scanner | Advertiser | GATT Client | GATT Server         │
│                  + GATT Operation Queue                   │
├───────────────────────────────────────────────────────────┤
│                    Android BLE APIs                       │
└───────────────────────────────────────────────────────────┘
```

### Architectural rule

**BLE transport must not contain mesh-routing logic.**

The BLE layer reports events:

```text
"NODE-B discovered"
"NODE-B connected"
"PACKET received from NODE-B"
"NODE-B disconnected"
```

The Mesh Coordinator decides:

```text
"NODE-B is a candidate relay"
"PACKET is new → store + forward"
"PACKET is duplicate → drop"
"NODE-B unavailable → use another relay"
```

This separation makes debugging and testing much easier.

---

# 3. BLE Concepts Relevant to RescueMesh

## 3.1 Scanning = discovery

Use:

- `BluetoothManager`
- `BluetoothAdapter`
- `BluetoothLeScanner`
- `ScanFilter`
- `ScanSettings`
- `ScanCallback`
- `ScanResult`

`BluetoothLeScanner` provides BLE scan operations and can filter results using `ScanFilter`.

Official reference:
https://developer.android.com/reference/android/bluetooth/le/BluetoothLeScanner

Recommended discovery model:

```text
Every RescueMesh phone:

ADVERTISE RescueMesh service
          +
SCAN for RescueMesh service
```

Do not scan for every Bluetooth device if the application can filter by the RescueMesh service UUID.

---

## 3.2 Advertising = "I am a RescueMesh node"

Use:

- `BluetoothLeAdvertiser`
- `AdvertiseSettings`
- `AdvertiseData`
- `AdvertiseCallback`

Official reference:
https://developer.android.com/reference/android/bluetooth/le/BluetoothLeAdvertiser

Advertising should contain only small discovery metadata, for example:

```text
Service UUID = RescueMesh
Node ID      = A17
Protocol     = v1
Role         = RELAY
```

Do **not** put the entire SOS message into advertising data.

The Android API documents a 31-byte advertisement-data limit for the legacy advertiser. Extended advertising has different limits, but the MVP should keep advertisements small and portable.

---

# 4. GATT Architecture

BLE advertising/scanning answers:

> "Which RescueMesh nodes are nearby?"

GATT answers:

> "How do two connected nodes exchange application data?"

Recommended custom GATT service:

```text
RescueMesh Service
│
├── RX Characteristic
│   └── WRITE / WRITE_NO_RESPONSE
│
├── TX Characteristic
│   └── NOTIFY
│
└── NodeInfo Characteristic
    └── READ (optional)
```

### RX

The remote node writes a packet to the local node.

### TX

The local node sends/forwards a packet through notifications.

### NodeInfo

Optional metadata:

```text
nodeId
role
protocolVersion
```

Important Android classes:

- `BluetoothGatt`
- `BluetoothGattCallback`
- `BluetoothGattCharacteristic`
- `BluetoothGattService`
- `BluetoothGattServer`
- `BluetoothManager`

Official GATT connection guide:
https://developer.android.com/develop/connectivity/bluetooth/ble/connect-gatt-server

---

# 5. How Two Phones Communicate

Example:

```text
PHONE A                         PHONE B

Advertise  ──────────────────> Scan

ScanResult
    │
    ▼
BluetoothDevice
    │
    ▼
connectGatt()
    │
    ▼
GATT connection
    │
    ▼
discoverServices()
    │
    ▼
Find RescueMesh service
    │
    ▼
Find RX/TX characteristics
    │
    ▼
Enable TX notifications
    │
    ▼
Write packet to RX
    ─────────────────────────> Receive packet
```

Android's documented GATT flow uses `connectGatt()` to connect to a GATT server and `BluetoothGattCallback` to receive connection/service-operation callbacks.

Official guide:
https://developer.android.com/develop/connectivity/bluetooth/ble/connect-gatt-server

Minimal connection example:

```kotlin
private var bluetoothGatt: BluetoothGatt? = null

private val gattCallback = object : BluetoothGattCallback() {

    override fun onConnectionStateChange(
        gatt: BluetoothGatt,
        status: Int,
        newState: Int
    ) {
        when (newState) {
            BluetoothProfile.STATE_CONNECTED -> {
                gatt.discoverServices()
            }

            BluetoothProfile.STATE_DISCONNECTED -> {
                // Mark node unavailable.
                // Close the old GATT object.
                // Trigger discovery/retry policy.
            }
        }
    }

    override fun onServicesDiscovered(
        gatt: BluetoothGatt,
        status: Int
    ) {
        // Find RescueMesh service and RX/TX characteristics.
    }
}

fun connect(device: BluetoothDevice) {
    bluetoothGatt = device.connectGatt(
        context,
        false,
        gattCallback
    )
}
```

---

# 6. Android Permissions

For Android 12 / API 31+:

```xml
<uses-permission
    android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />

<uses-permission
    android:name="android.permission.BLUETOOTH_ADVERTISE" />

<uses-permission
    android:name="android.permission.BLUETOOTH_CONNECT" />
```

These are runtime permissions.

Meaning:

| Permission | Purpose |
|---|---|
| `BLUETOOTH_SCAN` | Discover BLE devices |
| `BLUETOOTH_ADVERTISE` | Advertise this device |
| `BLUETOOTH_CONNECT` | Connect/communicate with devices |

Official documentation:
https://developer.android.com/develop/connectivity/bluetooth/bt-permissions

### Location caveat

If Bluetooth scan results are used to derive physical location, location permission is required.

If the app does not derive physical location from Bluetooth scanning, Android permits the `neverForLocation` assertion, subject to the platform caveats.

Do not confuse:

```text
BLE scanning
```

with:

```text
GPS location of survivor
```

If RescueMesh uses Android Location APIs to capture SOS coordinates, those location permissions must be handled separately.

### Android 11 and lower

Legacy declarations may be needed:

```xml
<uses-permission
    android:name="android.permission.BLUETOOTH"
    android:maxSdkVersion="30" />

<uses-permission
    android:name="android.permission.BLUETOOTH_ADMIN"
    android:maxSdkVersion="30" />
```

The exact final manifest should be verified against the project's target/min SDK and the Android versions of the actual demo phones.

---

# 7. Application-Layer Multi-Hop Mesh

Android BLE does **not** automatically create the RescueMesh routing behavior.

The mesh is implemented above BLE.

Recommended first protocol:

## Controlled flooding + duplicate suppression + TTL

Example topology:

```text
             B
           /   \
          /     \
A -------         ------- D
          \     /
           \   /
             C
```

A creates:

```text
messageId = SOS-001
origin = A
ttl = 5
hopCount = 0
```

B receives it:

```text
Is SOS-001 already seen?

NO
→ store
→ mark seen
→ decrement TTL
→ increment hopCount
→ forward
```

C may also receive it.

If C receives the same `SOS-001` later:

```text
Already seen?
YES
→ drop
```

This avoids uncontrolled propagation.

---

# 8. Recommended Packet

Protocol Buffers can represent the application packet.

Conceptual structure:

```proto
message EmergencyMessage {
    string message_id = 1;
    string origin_node_id = 2;
    string message_type = 3;
    int64 timestamp = 4;
    uint32 ttl = 5;
    uint32 hop_count = 6;
    string payload = 7;
    double latitude = 8;
    double longitude = 9;
    string priority = 10;
}
```

For the MVP:

- keep packets small;
- avoid images/video;
- avoid large attachments;
- use deterministic fields;
- keep TTL/hop count explicit.

A small SOS packet is much easier to transfer reliably than a large arbitrary payload.

---

# 9. Duplicate Suppression

Every node needs a local seen-set.

Conceptually:

```kotlin
if (seenMessageIds.contains(message.messageId)) {
    return
}

seenMessageIds.add(message.messageId)
database.insert(message)
forward(message)
```

For production-quality behavior, persist message identity/state in Room rather than relying only on an in-memory set.

Recommended database entities:

```text
NodeEntity
-----------
nodeId
role
lastSeen
lastRssi
connectionState
```

```text
MessageEntity
-------------
messageId
originNodeId
type
payload
timestamp
ttl
hopCount
status
```

---

# 10. Node Liveness / Moving Node Detection

GATT disconnection is one signal.

Use:

```kotlin
override fun onConnectionStateChange(
    gatt: BluetoothGatt,
    status: Int,
    newState: Int
)
```

If:

```text
newState == STATE_DISCONNECTED
```

mark the node unavailable.

But do not rely only on GATT disconnect callbacks.

Maintain:

```text
node.lastSeen
node.lastRssi
node.connectionState
```

Whenever a scan result is received:

```kotlin
node.lastSeen = System.currentTimeMillis()
node.lastRssi = result.rssi
```

Application-level timeout policy:

```text
0–3 sec    ACTIVE
3–8 sec    STALE
>8 sec     LOST
```

These values are only example policy values and must be tuned by testing. Android does not guarantee these timing thresholds.

---

# 11. RSSI

`ScanResult.rssi` gives received signal strength.

Use it as a **link-quality heuristic**, not as an exact distance measurement.

Example policy:

```text
RSSI > -60 dBm       STRONG
-60 to -80 dBm       MEDIUM
< -80 dBm            WEAK
```

These thresholds are application policy, not universal Android/BLE standards.

RSSI can vary because of:

- phone orientation;
- walls;
- human bodies;
- reflections;
- interference;
- different phone antennas/chipsets.

Therefore:

```text
RSSI = useful signal-strength hint
RSSI != exact distance
```

Use RSSI as one input into relay selection, not the sole routing metric.

---

# 12. Reconnect / Retry

Recommended state machine:

```text
DISCONNECTED
     │
     ▼
DISCOVER
     │
     ▼
CONNECTING
     │
     ▼
CONNECTED
     │
     ▼
SERVICE_DISCOVERY
     │
     ▼
READY
     │
     ├── packet transfer
     │
     ▼
DISCONNECTED
     │
     ▼
RETRY / DISCOVER
```

Suggested retry backoff:

```text
Attempt 1 → immediate
Attempt 2 → ~1 sec
Attempt 3 → ~2 sec
Attempt 4 → ~5 sec
```

After repeated failure, stop aggressive reconnect attempts and let normal scanning discover the node again.

Do not make the whole mesh depend on `autoConnect=true`. For a predictable hackathon prototype, explicit discovery + connection state management is easier to reason about.

---

# 13. GATT Operation Queue — Critical

Do not execute multiple GATT operations simultaneously.

Bad:

```kotlin
gatt.writeCharacteristic(...)
gatt.writeCharacteristic(...)
gatt.requestMtu(...)
gatt.discoverServices()
```

without waiting for callbacks.

Recommended:

```text
CONNECT
   ↓ callback
DISCOVER_SERVICES
   ↓ callback
SETUP_NOTIFICATIONS
   ↓ callback
WRITE_PACKET
   ↓ callback
WRITE_NEXT_PACKET
```

Implement:

```text
BleOperationQueue
```

and allow only one active GATT operation per connection at a time.

This should be treated as a core implementation requirement, not an optimization.

---

# 14. BLE Payload Limitations

Do not assume BLE can transport arbitrary-size emergency data in one operation.

For the MVP:

```text
SOS packet < ~300 bytes
```

is a practical design target.

Keep:

- message ID;
- origin;
- timestamp;
- priority;
- short payload;
- coordinates;
- TTL;
- hop count.

Avoid:

- photos;
- videos;
- PDFs;
- voice;
- large JSON blobs.

If large packets become necessary later, add fragmentation/reassembly. Do not make fragmentation part of the first 15-hour implementation unless the core mesh is already stable.

---

# 15. Background Operation Risk

This is a major Android constraint.

Do not assume:

> "startScan() means the phone will continuously act as a mesh relay forever."

Android has background/power-management constraints. The official scanner documentation specifically notes behavior differences for unfiltered scanning when the screen turns off.

For the hackathon MVP:

```text
App foreground
+
screen on
+
BLE active
```

should be the primary demo configuration.

If time remains, investigate a foreground service for persistent relay behavior.

Do not make background reliability a dependency of the first working demo.

---

# 16. Common Failure Cases

## Bluetooth disabled

Expected behavior:

```text
Show "Bluetooth required"
Do not crash
```

## Permission denied

Handle:

```text
BLUETOOTH_SCAN
BLUETOOTH_ADVERTISE
BLUETOOTH_CONNECT
```

independently.

## Scan started twice

Avoid calling `startScan()` repeatedly from UI recompositions.

Possible scan failure:

```text
SCAN_FAILED_ALREADY_STARTED
```

## Scan throttling

Do not rapidly perform:

```text
start
stop
start
stop
start
stop
```

Use a controlled scanner lifecycle.

## GATT 133 / unexpected GATT errors

Treat unexpected GATT failures as recoverable:

```text
close old GATT
clear connection state
rediscover
retry
```

Do not build application logic around one specific GATT error code.

## Stale BluetoothGatt object

After a failed/disconnected connection:

```kotlin
gatt.close()
```

and clear the stored connection reference before creating the next connection.

## Phone goes out of range

Interpret it as:

```text
NODE-B temporarily unavailable
```

not:

```text
SOS permanently lost
```

The Mesh Coordinator should seek another available relay.

## Screen locks

Test explicitly. Do not assume foreground scanning behavior remains unchanged after screen lock.

## OEM/device differences

Two Android phones can behave differently even at the same Android API level.

Freeze the actual demo devices early.

---

# 17. Recommended Project Structure

```text
com.rescuemesh
│
├── ble/
│   ├── BleScanner.kt
│   ├── BleAdvertiser.kt
│   ├── BleGattClient.kt
│   ├── BleGattServer.kt
│   ├── BleConnection.kt
│   └── BleOperationQueue.kt
│
├── mesh/
│   ├── MeshManager.kt
│   ├── MessageRouter.kt
│   ├── DuplicateTracker.kt
│   └── NodeManager.kt
│
├── data/
│   ├── AppDatabase.kt
│   ├── MessageEntity.kt
│   ├── NodeEntity.kt
│   └── MessageDao.kt
│
├── protocol/
│   └── emergency.proto
│
├── service/
│   └── MeshForegroundService.kt
│
└── ui/
    ├── Dashboard/
    ├── Sos/
    └── CommandCenter/
```

---

# 18. Testing Procedure

## Test 1 — Two phones

Topology:

```text
A ↔ B
```

Verify:

- [ ] Bluetooth enabled
- [ ] permissions granted
- [ ] A advertises
- [ ] B discovers A
- [ ] B advertises
- [ ] A discovers B
- [ ] GATT connection succeeds
- [ ] service discovery succeeds
- [ ] RX/TX characteristics found
- [ ] HELLO packet transfers
- [ ] SOS packet transfers
- [ ] disconnect detected
- [ ] reconnect succeeds

Do not proceed until this is stable.

---

## Test 2 — Three phones

Topology:

```text
A → B → C
```

Verify:

```text
A creates SOS-001
B receives SOS-001
B stores SOS-001
B forwards SOS-001
C receives SOS-001
```

Record:

```text
messageId
hopCount
creationTimestamp
destinationReceiveTimestamp
latency
```

---

## Test 3 — Five phones

Create multiple possible paths:

```text
        B
      /   \
A ---       --- E
      \   /
        C
        |
        D
```

Test:

- [ ] multi-hop forwarding
- [ ] duplicate suppression
- [ ] TTL
- [ ] node disappearance
- [ ] alternate relay
- [ ] reconnection
- [ ] local persistence

---

## Test 4 — Failure/recovery

Initial:

```text
A → B → C → D
```

Move B away.

Expected:

```text
B = LOST
```

Introduce another available node.

Expected:

```text
A → E → C → D
```

The exact alternate path depends on physical phone placement.

---

## Test 5 — Offline test

Disable:

```text
Mobile data
Wi-Fi / Internet
```

Keep:

```text
Bluetooth ON
```

Verify:

```text
SOS still travels
```

The application must not depend on:

```text
Firebase
REST API
Cloud database
Internet map API
External server
```

---

# 19. Risk Ranking for a 24-Hour Hackathon

| Risk | Severity | Mitigation |
|---|---:|---|
| BLE doesn't work reliably on chosen phones | CRITICAL | Test actual devices immediately |
| GATT operations collide | CRITICAL | Operation queue |
| Multi-hop forwarding fails | CRITICAL | Build 2-phone first, then 3-phone |
| Background restrictions | HIGH | Keep MVP foreground |
| Permission issues | HIGH | Test permissions before networking |
| Reconnect/GATT errors | HIGH | Explicit state machine + cleanup |
| Five-device topology instability | HIGH | Controlled physical layout |
| Packet size | MEDIUM | Keep SOS packets tiny |
| RSSI instability | MEDIUM | Treat RSSI as heuristic |
| UI bugs | LOW | Freeze core networking first |

---

# 20. What the BLE Engineer Should NOT Build

Do not spend hackathon time on:

- cloud backend;
- Firebase;
- login/signup;
- Internet APIs;
- AI chatbot;
- live Internet map;
- image/video transfer;
- complex routing algorithms;
- full production-grade background networking;
- replacing satellite/radio infrastructure.

First prove:

```text
A → B
```

then:

```text
A → B → C
```

then:

```text
A → B → C → D
```

then:

```text
A → B → C ❌
A → E → C → D ✓
```

That progression is the actual proof of concept.

---

# 21. Five Rules for the Lead Developer

1. **BLE discovery is not communication.**
2. **GATT operations must be serialized.**
3. **Mesh routing is application-layer logic.**
4. **RSSI is a signal-strength heuristic, not exact distance.**
5. **A disconnected relay does not mean the SOS is permanently lost.**

---

# 22. Immediate Engineering Milestone

The first implementation milestone is deliberately small:

```text
PHONE A                     PHONE B

Advertise  ───────────────→ Discover

              BLE

Send "HELLO" ─────────────→ Receive "HELLO"
```

Only after this works on the **actual hackathon phones** should the team implement multi-hop forwarding.

---

# 23. Official Android References

- Bluetooth permissions:
  https://developer.android.com/develop/connectivity/bluetooth/bt-permissions

- BLE scanner:
  https://developer.android.com/reference/android/bluetooth/le/BluetoothLeScanner

- BLE advertiser:
  https://developer.android.com/reference/android/bluetooth/le/BluetoothLeAdvertiser

- BLE/GATT package:
  https://developer.android.com/reference/android/bluetooth/le/package-summary

- Connect to a GATT server:
  https://developer.android.com/develop/connectivity/bluetooth/ble/connect-gatt-server

---

## Research conclusion

The technically defensible architecture is:

```text
Android BLE
    ↓
Advertising + Scanning
    ↓
GATT connection
    ↓
Small application packet
    ↓
Mesh Coordinator
    ↓
Deduplication + TTL + local storage
    ↓
Forward to available neighbors
    ↓
Failure detection
    ↓
Alternative relay
    ↓
Rescue node
```

RescueMesh should be described as an **application-layer store-and-forward emergency mesh built using Android BLE primitives**, not as Android's built-in Bluetooth Mesh and not as a replacement for satellite/radio emergency infrastructure.

The most important unresolved engineering question is not "Can Android BLE work?" It is:

> **Can the exact phones selected for the demo reliably sustain the discovery → GATT → packet-transfer → disconnect → rediscovery cycle needed for the multi-hop prototype?**

That must be tested on hardware before the team commits to the full architecture.
