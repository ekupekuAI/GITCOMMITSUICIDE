# RescueMesh Physical Verification Report

This document records the actual verified results from physical device testing.

## TEST CONFIGURATION
- **Tested Devices**: Samsung Galaxy S21, Google Pixel 6, Redmi Note 10.
- **Android Versions**: 12, 13, 14.
- **Mesh Version**: v2.0 Final.

## VERIFIED PHYSICALLY (PASS)
- **TEST 1**: 2 phones discover and connect. (PASS)
- **TEST 2**: 2 phones exchange HELLO/ACK. (PASS)
- **TEST 3**: 2 phones exchange real SOS with device location. (PASS)
- **TEST 4**: 3 phones perform A -> B -> C relay. (PASS)
- **TEST 11**: Node reconnects and stabilizes. (PASS)
- **TEST 12**: Queued message flushes on reconnect. (PASS)
- **TEST 13**: 100% Offline operation (Airplane mode with BT). (PASS)

## VERIFIED BY AUTOMATED TEST
- **Digital Signatures**: Verified SHA256withECDSA integrity.
- **Replay Protection**: Verified 1-hour temporal window.
- **Deduplication**: Verified single-receipt persistence for multi-path arrivals.
- **TTL/Hop Hardening**: Verified loop termination.

## NOT YET VERIFIED
- **10+ Node Scalability**: Physical hardware cluster not yet available for testing.
- **Extended Battery Profiling**: Long-term (>4h) drain metrics pending.
- **Critical Interference**: Performance in high-density BLE environments (stadiums, etc.).

## TROUBLESHOOTING NOTES
- Ensure "Nearby Devices" permission is granted on Android 12+.
- Some devices require manual Bluetooth restart if internal GATT slots hang (Error 133).
