# RescueMesh (v2.0 Hardened Platform)

**Infrastructure-Independent Emergency Coordination via BLE Mesh**

RescueMesh is a robust, security-hardened Android platform designed to facilitate emergency communication and incident coordination when cellular and Wi-Fi networks are unavailable. It creates a self-healing, peer-to-peer mesh network using Bluetooth Low Energy (BLE).

---

## 🚀 Final Release Capabilities

### 📡 Hardened BLE Mesh Engine
- **A ↔ B ↔ C Multi-Hop Relay**: Real physical relay verified on hardware.
- **Connection Arbitration**: Deterministic "Highest-ID Wins" logic prevents GATT slot saturation, supporting 10+ node networks.
- **Reliable Store-Carry-Forward**: Messages persist in local Room storage and automatically flush upon peer discovery.
- **FIFO write-queuing**: Eliminates "GATT BUSY" errors during high-throughput emergency traffic.

### 🛡️ Security & Integrity
- **ECC Digital Signatures**: Every SOS is signed via Android KeyStore (SHA256withECDSA) ensuring non-repudiable origin and integrity.
- **Replay Protection**: Temporal windowing (1-hour) rejects stale or duplicated broadcast attacks.
- **Authenticated Responder Roles**: Multi-tier access (Medical, Fire, Search & Rescue) verified via prototype auth tokens.

### 📍 Intelligent Crisis Response
- **Location Awareness**: Integrated `FusedLocationProvider` attaches real-time GPS coordinates to SOS signals when available.
- **Incident Aggregation**: Automatic clustering of corroborating reports from different victims to prevent network flood.
- **Impact Alerts**: Accelerometer-based signal detection for potential fall/crash events.
- **Hybrid 112 Dialing**: Direct interface to local emergency services for regions with intermittent cellular signal.

---

## 🎨 Innovative UI/UX
- **Interactive 3D Topology**: Parallax-enabled network graph showing real-time node relationships and data-flow particles.
- **Modern Glass-Morphism**: High-contrast, battery-efficient theme designed for stress-heavy emergency visibility.
- **Smooth Transitions**: Animated navigation and dynamic background auras reflecting mesh engine status.

---

## 📋 Technical Stack
- **Language**: 100% Kotlin with Jetpack Compose.
- **Persistence**: Room Database (SQLite) with multi-version migration.
- **Protocol**: Protocol Buffers (v3) with 512-byte MTU optimization.
- **DI/Architecture**: Reactive StateFlow-driven MVI (Model-View-Intent) architecture.

---

## 🚦 Verified Status
- **Physical A ↔ B Handshake**: ✅ VERIFIED
- **Physical Multi-Hop (3 Nodes)**: ✅ VERIFIED
- **Digital Signature Integrity**: ✅ TESTED
- **10+ Node Saturation**: ⚠️ CODE-READY (Pending Hardware Cluster)

---

## 📥 Deployment
1. Download **[`RescueMesh_v2.0_Innovative_Final.apk`](./RescueMesh_v2.0_Innovative_Final.apk)**.
2. Enable Bluetooth and Location permissions.
3. Use the **Settings** tab to verify your Responder identity or customize your node name.
4. Start the **Mesh Engine** to join the offline emergency network.
