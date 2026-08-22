# RescueMesh Real-World Roadmap

This document outlines the engineering path to evolve RescueMesh into a production-grade offline emergency coordination platform.

## 1. Safety & Reliability (HIGH PRIORITY)
- [ ] **End-to-End Delivery Tracking**: Harden persistent message states (CREATED -> RELAYED -> DELIVERED).
- [ ] **Adaptive Retransmission**: Implement exponential backoff for failed GATT writes.
- [ ] **Routing Hardening**: Capability-aware routing (route toward Responders/Gateways).
- [ ] **Loop & Duplicate Stress Testing**: Formalize rejection of re-played or looping packets.

## 2. Real-World Usefulness
- [ ] **Responder Roles**: Explicit opt-in for MEDICAL, FIRE, SEARCH_RESCUE, etc.
- [ ] **Incident Aggregation**: Cluster multiple reports of the same event into a single "Incident".
- [ ] **Hybrid Call Path**: Integration with native emergency calling when cellular is available.
- [ ] **Offline Maps (Local Metadata)**: Basic bearing/distance to incidents using local coordinates.

## 3. Security & Privacy
- [ ] **Privacy-by-Design**: Mask sensitive location/medical data for normal relay nodes.
- [ ] **Role Verification**: Cryptographically associate roles with node identities.
- [ ] **Replay Protection**: Add nonces or sequence numbers to signatures.

## 4. Scalability & Efficiency
- [ ] **Controlled Topology**: Dynamic neighbor table pruning based on RSSI and liveness.
- [ ] **Energy Profiles**: Finalize Low-Power vs. Emergency radio modes.
- [ ] **15+ Node Testing**: Physical verification of sparse network performance.

## 5. UI/UX Innovation
- [ ] **Clarity-First Design**: Streamlined "Emergency Mode" vs "Responder Dashboard".
- [ ] **Interactive Visuals**: Enhanced 3D Topology with "Data Flow" animations.
- [ ] **Simplified Onboarding**: Zero-config permissions and role selection.
