# RescueMesh Physical Device Testing

These tests require physical Android devices with BLE. Desktop unit tests do not verify radio range, GATT scheduling, or multi-device routing. Record observations from the devices; do not pre-fill results.

## Common Setup

- Install the same debug APK on every phone.
- Turn Wi-Fi and mobile data off. Keep Bluetooth and Location on.
- Grant Bluetooth, Location, and notification permissions.
- Clear local mesh data on every phone before each independent test.
- Start the mesh engine on every phone.
- Use Diagnostics and logcat to record `DISCOVERED`, `CONNECTED`, `RELAY`, `DELIVERED`, `DISCONNECTED`, and error events.

## Test A: Two-Device Discovery

Use phones A and B within BLE range. Start the engine on both. Confirm each phone discovers the other, reaches `READY`, and shows the peer in Nearby/Topology.

Result: ____________________

## Test B: Two-Device Message Exchange

Send an SOS from A with B nearby. Confirm B receives the same message ID, source node, category, and hop information. Confirm the sender receives the real message ACK.

Result: ____________________

## Test C: Three-Device Multi-Hop

Place B between A and C so A cannot directly reach C. Send from A. Confirm A -> B -> C, increasing hop count, duplicate suppression, and no forwarding back to the previous hop.

Result: ____________________

## Test D: Four-or-More Device Network

Arrange A, B, C, and D across overlapping BLE ranges. Confirm each node maintains only its real links and that an SOS reaches every reachable branch without claiming unlimited range.

Result: ____________________

## Test E: Duplicate Message

Send a message, then force a retry or duplicate path. Confirm one logical message is stored and duplicate receipt is rejected in Room.

Result: ____________________

## Test F: TTL Expiry

Use a message with a low TTL in a controlled build/test setup. Confirm forwarding stops at TTL zero and never becomes negative.

Result: ____________________

## Test G: Node Disconnect

Disconnect the next-hop phone after an SOS is queued. Confirm the message remains locally persisted and no false delivery status is shown.

Result: ____________________

## Test H: Alternate Route

With at least two reachable branches, remove the preferred next hop. Confirm a different valid neighbor can receive the persisted message and that logs show the actual route used.

Result: ____________________

## Test I: Reconnect and Queue Flush

Create an SOS while a neighbor is unavailable. Restore Bluetooth/range and complete the handshake. Confirm the queued message is sent after reconnection.

Result: ____________________

## Test J: Offline Internet

Disable Wi-Fi and mobile data on every phone. Confirm SOS creation, Room persistence, BLE discovery, relay, and response continue without cloud or REST access.

Result: ____________________

## Test K: Responder Delivery

Use A as victim, B and C as relays, and D as an authorized responder. Confirm A -> B -> C -> D, with real delivery and responder visibility of the required emergency information.

Result: ____________________

## Test L: Responder Response to Origin

On D, acknowledge the SOS or mark it responding with an optional short response. Confirm the response travels D -> C -> B -> A, is persisted, correlated to the SOS ID, and appears on A's dashboard.

Result: ____________________

## Automated Checks

Run from the repository root:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

These checks validate code and build integrity only. They do not replace Tests A-L on physical devices.
