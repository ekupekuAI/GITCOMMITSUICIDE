# RescueMesh Scalability Report

## TESTED NETWORK SIZE
- Current Verified Limit: 3 Devices (A -> B -> C)
- Target: 10-15 Devices

## PERFORMANCE MEASUREMENTS
| Node Count | Mesh Topology | Message Delivery Rate | Avg Latency |
| :--- | :--- | :--- | :--- |
| 2 | Point-to-Point | [BLANK] | [BLANK] ms |
| 3 | Linear Relay | [BLANK] | [BLANK] ms |
| 5 | Sparse Web | [BLANK] | [BLANK] ms |

## CONNECTION STRATEGY
- **Max Connections per Node**: 3 Client slots (Hard Limit in `MeshConfig.kt`).
- **Arbitration**: Deterministic "Higher ID Wins" logic to prevent redundant links.
- **Pruning**: Neighbors are marked STALE after 15s and LOST after 45s of inactivity.

## SATURATION POINTS
- Android BLE Hardware: Most devices fail to maintain more than 7 total GATT slots.
- MTU Throughput: 512-byte MTU enables single-write delivery for all current message types.
