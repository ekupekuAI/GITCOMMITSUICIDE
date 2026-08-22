# RescueMesh Physical Test Results (Phase 14 & 15)

This document records the measured performance and reliability of the RescueMesh system across real Android hardware.

## TEST CONFIGURATION
- **Tested Devices**: [BLANK] (e.g., Pixel 7, Samsung S22, etc.)
- **Android Versions**: [BLANK]
- **Mesh Version**: v1.5 Hardened

## PHASE 14: CORE FUNCTIONALITY
| Test | Description | Result (PASS/FAIL) | Notes |
| :--- | :--- | :--- | :--- |
| TEST 1 | 2 phones discover and connect | [BLANK] | |
| TEST 2 | 2 phones exchange HELLO/ACK | [BLANK] | |
| TEST 3 | 2 phones exchange real SOS | [BLANK] | |
| TEST 4 | 3 phones perform A -> B -> C relay | [BLANK] | |
| TEST 5 | 4+ phones form multiple paths | [BLANK] | |
| TEST 6 | Duplicate message arrives through multiple paths | [BLANK] | |
| TEST 7 | TTL reaches zero (No further relay) | [BLANK] | |
| TEST 8 | Message expires (No further relay) | [BLANK] | |
| TEST 9 | Relay node disappears (Loss detected) | [BLANK] | |
| TEST 10 | Alternate route succeeds | [BLANK] | |
| TEST 11 | Node reconnects | [BLANK] | |
| TEST 12 | Queued message flushes on reconnect | [BLANK] | |
| TEST 13 | Wi-Fi + Mobile Data OFF | [BLANK] | |
| TEST 14 | No direct internet dependency | [BLANK] | |

## PHASE 15: PERFORMANCE METRICS (Averages)
| Metric | Measured Value | Target |
| :--- | :--- | :--- |
| Discovery Time (avg) | [BLANK] ms | < 10s |
| Connection Time (avg) | [BLANK] ms | < 5s |
| Single-Hop Latency | [BLANK] ms | < 2s |
| Multi-Hop Latency (3 nodes) | [BLANK] ms | < 5s |
| Delivery Success Rate | [BLANK] % | > 95% |
| Duplicate Rejection Rate | [BLANK] % | 100% |
| Impact Detection Latency | [BLANK] ms | < 500ms |

## BATTERY OBSERVATIONS
- **Normal Mode (1 hour)**: [BLANK] % drain
- **Low Power Mode (1 hour)**: [BLANK] % drain
- **Urgent Mode (1 hour)**: [BLANK] % drain

---
**Verification Date**: [BLANK]
**Tester Name**: [BLANK]
