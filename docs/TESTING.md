# RescueMesh Testing Guide

This project requires physical Android devices for verification, as emulators cannot reliably simulate multi-device BLE mesh behavior.

## 1. Setup
Ensure all test devices:
- Have Bluetooth enabled.
- Have Location (GPS) enabled.
- Are running Android 8.0 or newer.
- Are within 10 meters of each other for the initial handshake.

## 2. Verified Test Sequence

### Test A: Peer Discovery (2 Devices)
1. Install and open RescueMesh on Phone A and Phone B.
2. Grant Bluetooth and Location permissions.
3. Tap **Start Engine** on both.
4. Verify Phone B appears on Phone A's "Nearby" screen within 15 seconds.
5. Verify a green pulsing line appears in the **Topology** view.

### Test B: Single-Hop SOS
1. Perform Test A.
2. On Phone A, go to the **SOS** tab and trigger a medical emergency.
3. Verify Phone B receives the alert and displays the notification card.
4. Verify Phone A updates the message status to **"RELAYED"**.

### Test C: Multi-Hop Relay (3 Devices)
1. Set up Phones A, B, and C.
2. Move Phone C out of range of A, but kept Phone B in the middle as a bridge.
3. Trigger an SOS on A.
4. Verify Phone B receives it first, then Phone C receives it shortly after via B.
5. Check Phone C's log to see **`hop_count = 1`**.

### Test D: Persistence & Recovery
1. Turn off Bluetooth on all neighbors.
2. Create an SOS on Phone A.
3. Restart the app on Phone A.
4. Turn Bluetooth back on for Phone B.
5. Verify Phone A automatically flushes the stored SOS to B once the handshake completes.

## 3. Automated Verification
Run the internal unit tests to verify logic correctness without hardware:
```powershell
.\gradlew.bat testDebugUnitTest
```
These tests cover:
- Protocol framing and length limits.
- Relay policy (TTL decrementing).
- Signature verification (Sign/Verify cycles).
- Database deduplication.
