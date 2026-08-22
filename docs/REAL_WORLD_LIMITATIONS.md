# Real-World Limitations

## BLE RANGE
- Outdoor (Line of Sight): ~20-50 meters.
- Indoor (Obstacles): ~5-15 meters depending on wall density.

## HARDWARE CONSTRAINTS
- **GATT Slots**: Android phones are limited by hardware to ~4-7 concurrent BLE connections.
- **Background Execution**: Most Android versions aggressively kill background BLE services. App must remain in foreground for reliable mesh participation.

## LOCATION ACCURACY
- FusedLocationProvider depends on GPS/Network. In disaster areas, accuracy may degrade if towers are down.

## SECURITY
- Prototype uses ECC signatures for integrity.
- Replay protection is limited to a 1-hour window based on local clock.
- No central PKI: Node roles are self-declared in this version.
