# RescueMesh Physical Device Testing Guide

Use Android phones with Bluetooth enabled. Keep the app in the foreground and the screen on. Do not enable Wi-Fi, cellular data, Firebase, Supabase, REST APIs, or any cloud service for these tests.

## Build and Install

```powershell
cd D:\MeshOS
.\gradlew.bat assembleDebug
```

Install `app/build/outputs/apk/debug/app-debug.apk` on each test phone.

## 1 Phone

1. Open RescueMesh.
2. Grant BLE scan, advertise, and connect permissions.
3. Tap `Start discovery`.
4. Confirm the screen shows:
   - local `NODE-...` ID
   - `No internet`
   - advertising/scanning status
   - no crash when no peers exist
5. Create an SOS.
6. Confirm the SOS appears in the stored list as locally persisted.

Expected result: local offline storage works. No relay is claimed.

## 2 Phones

1. Open the app on Phone A and Phone B.
2. Grant BLE permissions on both.
3. Tap `Start discovery` on both.
4. Wait up to 30 seconds.
5. Confirm each phone shows a nearby RescueMesh peer with RSSI and last-seen age.
6. Confirm at least one phone reaches `HELLO`, `ACK`, or `CONNECTED` state.
7. Create an SOS on Phone A.
8. Confirm Phone B receives and stores the SOS.
9. Confirm Phone A marks the message as accepted by a peer after ACK.

Expected result: real two-device BLE discovery, connection, protobuf frame exchange, DATA write, Room persistence, and ACK.

## 3 Phones: Multi-Hop Relay

Goal: prove A -> B -> C when A cannot directly reach C.

1. Start Phone B in the middle.
2. Place Phone A close to B.
3. Place Phone C far enough from A that A does not discover C, but close enough to B that B discovers C.
4. Start discovery on all phones.
5. Confirm A sees B, B sees A/C, and C sees B.
6. Create an SOS on A.
7. Confirm B receives and stores it with hop count incremented.
8. Confirm C later receives the same SOS through B.
9. Confirm C shows hop count greater than A's original hop count.

Expected result: only claim multi-hop success if C receives A's real message while A and C are not directly connected.

## 4+ Phones

1. Start discovery on all phones.
2. Create SOS messages from two different phones.
3. Move phones around so different peers appear/disappear.
4. Confirm messages continue to be stored and relayed opportunistically.
5. Confirm the UI never shows fake distance, peer battery, or latency.

Expected result: multiple real BLE peers can participate without cloud services.

## Duplicate Packets

1. On two or more phones, keep discovery running.
2. Send one SOS.
3. Leave phones near each other for several minutes.
4. Move a relay phone away and then back.
5. Confirm each receiving phone stores only one row for the message ID.
6. Confirm repeated copies do not create duplicate SOS cards.

Expected result: `message_receipts.message_id` prevents duplicate processing.

## TTL Expiry

1. Temporarily create a test SOS with low TTL in code or via a debug build variant.
2. Relay it across enough phones to exhaust TTL.
3. Confirm receivers do not relay a message with `ttl == 0`.
4. Confirm expired messages are not forwarded after `expires_at_ms`.

Expected result: TTL decreases only on accepted relay copies, and expired messages do not propagate.

## Node Disconnect

1. Start two phones and confirm discovery/connection.
2. Turn Bluetooth off on Phone B or move it out of range.
3. Confirm Phone A marks the peer stale/lost or disconnected after the liveness window.
4. Confirm stored SOS messages remain in Room and are not deleted.

Expected result: disconnect does not lose stored messages.

## Reconnect

1. After the disconnect test, turn Bluetooth back on or move Phone B back into range.
2. Confirm discovery resumes.
3. Confirm the app attempts a real GATT reconnect after backoff.
4. Confirm pending SOS messages are sent after the peer handshakes.

Expected result: retry/reconnection uses BLE scan observations and GATT, not simulated routing.

## Offline Operation

1. Disable Wi-Fi and cellular data on all phones.
2. Optionally remove SIM cards or use airplane mode with Bluetooth re-enabled.
3. Repeat the 2-phone and 3-phone tests.

Expected result: discovery, exchange, persistence, and relay continue without internet.

## What Not To Claim

- Do not claim multi-hop works until the 3-phone test passes physically.
- Do not claim peer distance from RSSI.
- Do not claim peer battery percentage.
- Do not report latency unless measured by a real DATA-to-ACK sample.
