# About RescueMesh

## Project Vision
RescueMesh was born from a critical problem: **the total collapse of communication during natural disasters.** When cell towers go down, people in need cannot reach responders, and responders cannot coordinate efficiently. RescueMesh solves this by turning every phone into a node in a decentralized, offline network.

## How It Works
The app uses **Bluetooth Low Energy (BLE)** to discover nearby devices. Unlike traditional Bluetooth, BLE allows for continuous low-power background operations.

1.  **Discovery**: When you tap "Start Engine," your phone begins advertising its "RescueMesh Service UUID" and scanning for others.
2.  **Handshake**: When two phones find each other, they perform a "Highest-ID Wins" arbitration to establish a single, stable GATT connection.
3.  **The Mesh**: If Node A sends an SOS and Node B receives it, Node B checks if it has a better route (e.g., to a Responder). If not, B stores the message and re-broadcasts it to Node C.
4.  **Security**: To prevent "Fake SOS" messages, every report is cryptographically signed by the sender's device. Responders verify these signatures to ensure the request is legitimate.

## Key Features in Detail

### 1. The Responder Ecosystem
Not all nodes are equal. A user can opt-in as a **Medical Responder** or **Search & Rescue**. The routing engine is "capability-aware," meaning it will prioritize sending a medical SOS toward a Medical node rather than a generic relay.

### 2. Incident Aggregation
In a real crisis, 50 people might report the same fire. RescueMesh's **Correlation Engine** looks at the GPS coordinates and timestamps. If reports are within 100 meters of each other, it clusters them into one "Incident" on the Responder's dashboard, showing a "Corroboration Count" (e.g., "Fire: 50 reports"). This saves precious bandwidth.

### 3. Data Privacy
RescueMesh respects privacy even in chaos. **Public Relay** nodes (normal users) only see that a message is passing through them. They cannot read the private details of the SOS or see the victim's exact GPS location. Only **Authorized Responders** can decrypt and view the sensitive data.

### 4. Hardware Impact Detection
The app monitors the phone's **Accelerometer**. If a sudden high-G impact is detected followed by inactivity, the app can automatically flag the user's SOS with an "Impact Alert," signaling that the victim may be unconscious.

## Technical Requirements
- **Android 8.0+** (API 26)
- **Bluetooth 5.0+** recommended for range/throughput.
- **No Internet Required**: The core mesh is 100% infrastructure-independent.

## Future Roadmap
- **LoRa Integration**: Support for long-range radio hardware bridges.
- **Mesh Voice-Notes**: Fragmented audio delivery across the mesh.
- **Advanced PKI**: Integration with government-issued emergency certificates.

---
**RescueMesh**: Connect. Relay. Save.
