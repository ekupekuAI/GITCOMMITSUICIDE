# RescueMesh Wire Protocol

RescueMesh uses a framed Protocol Buffer schema over BLE GATT. This ensures compact serialization and efficient parsing on mobile hardware.

## 1. BLE Service Definition
The mesh operates on a single primary service with three characteristics:

- **Service UUID**: `8E400001-F315-4F60-9FB8-838830DAEA50`
- **Control Characteristic** (`WRITE_NO_RESPONSE`): Used for handshakes (HELLO) and small control frames.
- **Data RX Characteristic** (`WRITE_NO_RESPONSE`): Used by clients to write mesh messages to the server.
- **Data TX Characteristic** (`NOTIFY`): Used by the server to push ACKs and data to connected clients.

## 2. Wire Framing
Every characteristic value is wrapped in a 4-byte header to assist with versioning and multiplexing:

| Offset | Size | Field | Description |
| :--- | :--- | :--- | :--- |
| 0 | 1 | Version | Currently `0x01` |
| 1 | 1 | Type | `0x01` (Control), `0x02` (Data) |
| 2 | 2 | Length | Little-endian uint16 of payload size |
| 4 | n | Payload | The serialized Protobuf message |

## 3. Handshake Flow
1. **Connect**: Negotiate 512-byte MTU.
2. **Discover**: Find characteristics.
3. **Subscribe**: Client subscribes to Data TX notifications.
4. **HELLO**: Both nodes exchange `ControlMessage(HELLO)` containing their `node_id` and `NodeRole`.
5. **ACK**: Nodes acknowledge the HELLO to transition the link to `READY` state.

## 4. Message Schema (Protobuf)
The primary data structure is the `MeshMessage`:

- `message_id`: Unique 16-byte identifier.
- `origin_node_id`: ID of the device that created the SOS.
- `priority`: 0 (Critical) to 3 (Low).
- `ttl`: Remaining hops allowed (starts at 8).
- `location`: Optional GPS coordinates and accuracy.
- `signature`: SHA256 ECC signature of the message.
- `public_key`: The sender's public key required for verification.

## 5. Delivery States
Messages transition through deterministic states in the database:
- `CREATED`: Locally generated, not yet sent.
- `PERSISTED`: Securely stored in Room.
- `QUEUED`: Ready for relaying to neighbors.
- `RELAYED`: Received an ACK from at least one neighbor.
- `DELIVERED`: Reached an authorized responder or destination.
