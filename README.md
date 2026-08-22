# RescueMesh

RescueMesh is a decentralized emergency communication platform for Android that operates entirely without cellular or Wi-Fi infrastructure. It uses Bluetooth Low Energy (BLE) to create a self-healing mesh network, allowing people in disaster zones to broadcast SOS signals, share their location, and coordinate with responders.

## Problem
In many natural disasters, infrastructure like cell towers and power grids are the first to fail. Without connectivity, victims cannot signal for help, and responders lose the ability to coordinate efforts effectively.

## Solution
RescueMesh turns every smartphone into a network node. Even without a SIM card or internet access, devices can discover each other, establish secure links, and relay emergency messages across multiple hops until they reach an authorized responder or a gateway with connectivity.

## How it Works
RescueMesh implements an opportunistic store-and-forward mesh over BLE. Each device runs both a GATT server and a GATT client. When two devices discover each other, they perform a deterministic handshake to exchange node identities and capabilities. If a device has an SOS message in its local database, it attempts to relay it to neighbors based on their proximity and role (e.g., prioritizing medical responders).

## Key Capabilities
- **Infrastructure-Independent Mesh**: Real peer-to-peer relaying verified on physical hardware.
- **Store-and-Forward Routing**: Messages survive intermittent connectivity by persisting in a local Room database.
- **Verified Authenticity**: All emergency signals are digitally signed using the Android KeyStore (ECC SHA256) to prevent tampering.
- **Responder Roles**: Capability-aware routing that identifies and prioritizes nodes with specific skills (Medical, Fire, etc.).
- **Incident Aggregation**: Automatically clusters nearby similar reports into a single event to reduce network flood.
- **Self-Cleaning Storage**: Automatically purges expired messages, stale neighbor logs, and internal app cache on startup and shutdown.
- **Device Location**: Attaches real GPS coordinates to SOS messages when available (using FusedLocationProvider).
- **Hybrid Call Path**: One-touch dialing for local emergency services (112 in India) when cellular signal is present.

## Technology Stack
- **Language**: Kotlin
- **UI**: Jetpack Compose (Material 3)
- **Database**: Room (SQLite)
- **Serialization**: Protocol Buffers (v3)
- **Networking**: Android Bluetooth LE (GATT)
- **Security**: Android KeyStore (ECC SHA256)

## Project Structure
- `ble/`: Low-level BLE management (Advertiser, Scanner, GATT Client/Server).
- `mesh/`: Mesh logic, including connection arbitration and forwarding policies.
- `data/`: Persistence layer (Room entities, DAOs, and repositories).
- `protocol/`: Wire-format definitions and Protocol Buffer integration.
- `identity/`: Node identity, security key management, and location providers.
- `ui/`: Dashboard, SOS, and Topology visualization components.

## Setup and Build
To build the project, you need Android Studio Ladybug or newer and the Android SDK (API 35).

1. Clone the repository.
2. Open the project in Android Studio.
3. Sync Gradle and build the `:app` module.
4. Deploy `RescueMesh_v2.0_Final_Hardened.apk` to an Android device (API 26+).

## Physical Device Testing
RescueMesh requires physical hardware to verify BLE performance. Testing should be done with 3 or more devices in an environment with Wi-Fi and Mobile Data disabled.
1. Start the **Mesh Engine** on all devices.
2. Verify they appear in the **Nearby** list and **Topology** view.
3. Trigger an SOS on one device and confirm its delivery on other nodes.

## Security Approach
RescueMesh uses hardware-backed ECC keys to sign every SOS message at the source. Relaying nodes verify these signatures before forwarding to ensure data integrity. Replay protection is enforced via a persistent message receipt index and a 1-hour temporal window.

## Limitations
- **BLE Range**: Typical range is 20-50 meters in open space; significantly less indoors.
- **GATT Slots**: Android devices are hardware-limited to a few concurrent BLE connections (usually 4-7).
- **Foreground Usage**: For reliable mesh participation, the app should remain in the foreground with the screen on.
- **Prototype Auth**: In this version, responder roles are verified using a prototype code (RESCUE112).
