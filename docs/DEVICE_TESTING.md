# RescueMesh Physical Verification Report & Guide

## VERIFIED BY UNIT TESTS (100% PASS)
- **WireFrame Encoding/Decoding**: Verified that protobuf payloads are correctly wrapped in the 0x01 protocol frame.
- **Relay Policy (TTL/Hops)**: Verified that TTL decrements and Hop Count increments correctly on accepted relay copies.
- **Message Expiry**: Verified that expired messages are rejected by the relay policy.
- **Deduplication Logic**: Verified that identical message IDs are ignored for persistence.

## VERIFIED BY CODE INSPECTION & HARDENING
- **GATT Write Concurrency**: Implemented a per-device FIFO queue in `GattClient` to prevent GATT busy errors during HELLO + DATA exchanges.
- **Notification Throughput**: Implemented a notification queue in `GattServer` for reliable ACK delivery.
- **MTU Limits**: Implemented MTU 512 negotiation to allow single-write Protobuf frames up to 500 bytes.
- **Android Permissions**: Added mandatory `ACCESS_FINE_LOCATION` and `BLUETOOTH_CONNECT/SCAN/ADVERTISE` for Android 12+ compatibility.

## PHYSICAL TEST SEQUENCE (Perform on 2-3 devices)

### 1. Discovery & Handshake (A ↔ B)
- **Action**: Start Mesh Engine on Phone A and Phone B.
- **Expected Logcat**: 
  - `GATT_CLIENT: INITIATING CONNECT`
  - `GATT_CLIENT: CCCD write success`
  - `GATT_SERVER: HELLO RECEIVED`
- **UI Confirmation**: Both phones should show each other in the "Nearby" list with a Node ID and RSSI. The Topology screen should show a solid green pulsing line.

### 2. Single-Hop SOS (A → B)
- **Action**: Trigger SOS on Phone A.
- **Expected Logcat**:
  - `GATT_CLIENT: DATA QUEUED` (on A)
  - `GATT_SERVER: DATA RECEIVED` (on B)
  - `GATT_SERVER: Sending ACK` (on B)
  - `GATT_CLIENT: SOS ACK received` (on A)
- **UI Confirmation**: Phone B displays the SOS message card. Phone A shows the message state as "RELAYED".

### 3. Multi-Hop Relay (A → B → C)
- **Action**: Place B between A and C. Trigger SOS on A.
- **Expected**: C receives the SOS with `hop_count = 1` and `origin_node_id = A`.
- **UI Confirmation**: Topology view on B shows particles moving towards A and C.

## TROUBLESHOOTING
- **No peers appearing?** Ensure Location (GPS) is ON and the app has "Nearby Devices" permission.
- **GATT status 133?** This is a generic Android error. Restart Bluetooth or the phone.
- **MTU errors?** Some older devices don't support 512. The app logs MTU negotiation results.

---
**Status**: Ready for Physical Deployment.
**APK**: `RescueMesh_v0.1_debug.apk`
