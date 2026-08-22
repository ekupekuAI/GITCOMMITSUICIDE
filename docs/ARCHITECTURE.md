# RescueMesh Architecture

RescueMesh is an application-layer opportunistic store-carry-forward mesh network built on Android Bluetooth Low Energy. This document describes the internal components and data flow of the system.

## 1. System Overview
The system follows a reactive architecture where the UI observes state flows emitted by a central `MeshCoordinator`. Data persistence is the single source of truth, ensuring that messages are never lost even if the app process is interrupted.

## 2. Core Components

### 2.1 BLE Transport Layer
- **`BleAdvertiser`**: Manages the legacy BLE advertising broadcast. It keeps the primary payload under 31 bytes by moving device metadata into the scan response.
- **`BleScanner`**: Performs filtered scans for the RescueMesh service UUID. It uses aggressive matching modes to ensure all nearby nodes are discovered quickly.
- **`GattClient`**: Manages outgoing connections to neighbors. It implements a FIFO write queue to serialize GATT operations and requests a 512-byte MTU for high-throughput Protobuf frames.
- **`GattServer`**: Hosts the local mesh service. It handles incoming handshakes and data writes, confirming persistence before acknowledging receipt.

### 2.2 Mesh & Routing
- **`MeshCoordinator`**: The brain of the node. It orchestrates discovery, connection arbitration, and the overall message lifecycle.
- **`ForwardingPolicy`**: Logic that determines if a message is eligible for relaying based on its TTL, expiry, and the role of the neighbor.
- **`RelayPolicy`**: Handles the transformation of messages as they hop (e.g., decrementing TTL and incrementing hop count).
- **`Connection Arbitration`**: A deterministic "Highest-ID Wins" rule that prevents redundant symmetric connections between pairs of nodes, preserving limited hardware GATT slots.

### 2.3 Persistence & Identity
- **`MessageRepository`**: Provides a high-level API for the mesh engine and UI to interact with stored SOS messages, neighbors, and incidents.
- **`AppDatabase` (Room)**: A local SQLite database that stores the durable state of the mesh.
- **`NodeIdentityProvider`**: Generates and manages the stable 128-bit node identity and user-defined display names.
- **`SecurityProvider`**: Interfaces with the Android KeyStore to perform ECC SHA256 digital signatures on all outgoing SOS messages.

## 3. Data Flow

### 3.1 Message Creation
1. User triggers SOS → `LocationProvider` fetches current coordinates.
2. `SosFactory` creates a `MeshMessage` (Protobuf).
3. `SecurityProvider` signs the message hash.
4. `MessageRepository` persists the message with state `PERSISTED`.
5. `MeshCoordinator` triggers a flush to all active neighbors via `GattClient`.

### 3.2 Message Reception & Relay
1. `GattServer` receives a DATA write → Frame is decoded.
2. `SecurityProvider` verifies the signature using the included public key.
3. `RelayPolicy` checks TTL and expiry.
4. `MessageRepository` inserts a receipt; if it's a new ID, the message is stored as `QUEUED`.
5. `GattServer` sends an ACK to the sender.
6. `MeshCoordinator` re-broadcasts the message to other eligible neighbors.

## 4. UI & Diagnostics
The UI is built with **Jetpack Compose** and uses `collectAsStateWithLifecycle` to bind to the `MeshCoordinator.uiState`.
- **Topology Visualization**: A custom Canvas component that renders active connections and neighbor liveness in a 3D-rotatable space.
- **Diagnostics**: A real-time monitor showing RSSI, latency samples, success rates, and raw packet logs.
