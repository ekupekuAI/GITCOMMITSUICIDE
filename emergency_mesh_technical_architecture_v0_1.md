# Infrastructure-Independent Emergency Communication Mesh
## Technical Architecture & Research Baseline
**Document status:** Architecture baseline v0.1  
**Date:** 2026-08-22  
**Implementation baseline:** Android + Kotlin/Java + Bluetooth Low Energy + Protocol Buffers + SQLite/Room

> This is an engineering design document, not a product/marketing document. Items marked **TBD** must be validated experimentally on the target Android devices.

---

## 1. System objective

Build an Android application that can exchange short emergency messages without cellular data, Wi-Fi infrastructure, or a central server by using nearby Android devices as forwarding nodes.

The intended network model is an **opportunistic, store-carry-forward mesh**:

- Devices discover nearby participating devices over BLE.
- A node stores messages locally.
- When a suitable neighbor becomes available, the node transfers messages.
- A receiving node stores a message before attempting further forwarding.
- The message can therefore traverse multiple intermittent links.
- The network does not require a stable end-to-end path.
- Duplicate copies are suppressed using a globally unique message identifier.
- Routing decisions are local and adaptive rather than dependent on a central routing server.

### Important engineering distinction

This is not a conventional IP mesh router. BLE gives us short-range application-level links, while Android controls scanning, advertising, background execution, connection scheduling, and radio resources.

Therefore the architecture should be described as:

**application-layer opportunistic mesh over Android BLE**, not as an IP routing network.

---

# 2. High-level architecture

```text
+--------------------------------------------------------------+
|                        Android Application                   |
|                                                              |
|  +------------------+      +-------------------------------+ |
|  | SOS / Message UI |----->| Message Service               | |
|  +------------------+      | - validation                  | |
|                            | - message creation            | |
|                            +---------------+---------------+ |
|                                            |                 |
|                    +-----------------------v----------------+|
|                    | Mesh / Routing Engine                 ||
|                    | - neighbor scoring                    ||
|                    | - forwarding policy                   ||
|                    | - TTL/hop handling                    ||
|                    | - duplicate suppression               ||
|                    | - retry/backoff                        ||
|                    +-------------------+--------------------+|
|                                        |                     |
|          +-----------------------------v------------------+  |
|          | BLE Transport Manager                         |  |
|          | - scan / advertise                            |  |
|          | - GATT client/server                          |  |
|          | - connection lifecycle                        |  |
|          | - packet framing / chunking                    |  |
|          +-----------------------------+------------------+  |
|                                        |                     |
|          +-----------------------------v------------------+  |
|          | Persistence Layer                             |  |
|          | Room / SQLite                                |  |
|          | - messages                                    |  |
|          | - delivery state                              |  |
|          | - seen-message index                          |  |
|          | - neighbors / link observations               |  |
|          +-----------------------------------------------+  |
|                                                              |
+----------------------------+---------------------------------+
                             |
                         Android BLE
                             |
              +--------------+--------------+
              |                             |
          Nearby Node B                 Nearby Node C
              |                             |
        same application               same application
```

---

# 3. Major modules

## 3.1 Presentation / SOS module

Responsibilities:

- Display emergency message composer.
- Provide a prominent SOS action.
- Validate message size and required fields.
- Show local state:
  - queued
  - relaying
  - delivered
  - expired
  - failed/no route currently available
- Never assume that pressing SOS means immediate delivery.

Suggested API:

```text
createSos(payload)
cancelLocalDraft()
observeMessageStatus(messageId)
```

The UI must be decoupled from transport and routing.

---

## 3.2 Message Service

Creates the canonical application message.

Responsibilities:

1. Generate `message_id`.
2. Record `origin_node_id`.
3. Set creation time.
4. Set expiration time.
5. Set initial TTL/hop budget.
6. Set priority.
7. Serialize through Protocol Buffers.
8. Persist before attempting transmission.

**Critical invariant:**

> A message is durable before the first forwarding attempt.

This prevents loss if the process crashes immediately after SOS creation.

---

## 3.3 Identity module

Each installation needs an application-level node identity.

Recommended baseline:

```text
node_id = random 128-bit identifier
```

Do not use the Android Bluetooth MAC address as the application identity.

Reasons:

- modern Android privacy behavior;
- address randomization;
- coupling application identity to hardware identity;
- privacy leakage.

Persist the node ID in protected application storage.

A future hardened implementation can use a long-term public key as the node identity.

---

## 3.4 BLE Discovery module

Responsibilities:

- BLE advertising.
- BLE scanning.
- Filter participating nodes.
- Maintain recent sightings.
- Trigger connection attempts according to policy.
- Avoid continuous unrestricted scanning.

Advertising should expose only minimal metadata.

Conceptual advertisement:

```text
Service UUID
Protocol version
Ephemeral/session identifier or short node token
Capabilities
```

Do not put the full SOS payload in advertisements.

Android's BLE APIs distinguish the central/peripheral roles and GATT client/server roles, so the implementation should explicitly support the required combination rather than assuming every phone behaves like a conventional BLE peripheral. Android documentation also warns that scanning is battery-intensive and should be time-limited. Background execution is subject to Android system restrictions. 

---

## 3.5 BLE Transport Manager

Responsibilities:

- Create GATT client connections.
- Host the GATT server.
- Discover the application service.
- Exchange protocol frames.
- Handle MTU/chunking.
- Serialize access to GATT operations.
- Detect disconnects.
- Retry with backoff.

Recommended service model:

```text
Mesh Service
|
+-- Control characteristic
|     HELLO
|     INVENTORY_REQUEST
|     INVENTORY_RESPONSE
|     ACK
|
+-- Data characteristic
      DATA_FRAME
```

The exact characteristic layout is an implementation detail and must be tested against target devices.

Do not assume one write equals one complete Protocol Buffer message. The transport layer must provide its own framing.

Example frame:

```text
[protocol_version]
[frame_type]
[payload_length]
[payload_bytes]
```

The payload bytes contain a serialized Protocol Buffer.

---

# 4. Protocol Buffers message model

Protobuf is appropriate for the application protocol because it gives compact typed serialization and generated Kotlin/Java bindings.

A conceptual schema:

```proto
message MeshMessage {
  bytes message_id = 1;
  bytes origin_node_id = 2;
  bytes destination_id = 3;
  MessageType type = 4;
  uint32 priority = 5;
  uint32 ttl = 6;
  uint64 created_at_ms = 7;
  uint64 expires_at_ms = 8;
  uint32 payload_version = 9;
  bytes payload = 10;
  bytes signature = 11;
}
```

Possible control messages:

```proto
message Hello {
  bytes node_id = 1;
  uint32 protocol_version = 2;
  uint64 timestamp_ms = 3;
  uint32 capability_flags = 4;
}

message InventoryRequest {
  repeated bytes message_ids = 1;
}

message InventoryResponse {
  repeated MessageSummary summaries = 1;
}

message MessageSummary {
  bytes message_id = 1;
  uint32 ttl = 2;
  uint64 expires_at_ms = 3;
  uint32 priority = 4;
}

message DeliveryAck {
  bytes message_id = 1;
  bytes destination_id = 2;
  uint64 received_at_ms = 3;
}
```

### Protocol rule

Unknown fields must be safely ignored so future protocol versions can evolve without breaking older nodes.

The schema must reserve removed field numbers rather than reusing them.

---

# 5. Room / SQLite data model

Room provides the application-facing abstraction over SQLite, with entities, DAOs and a database class.

Recommended tables:

## `node_identity`

| Field | Purpose |
|---|---|
| node_id | local stable application identity |
| created_at | identity creation time |
| key_version | security/key version |

## `messages`

| Field | Purpose |
|---|---|
| message_id | primary key / duplicate key |
| origin_node_id | original sender |
| destination_id | intended recipient or broadcast group |
| type | SOS / text / ACK / control |
| priority | forwarding priority |
| ttl | remaining hop budget |
| created_at | creation timestamp |
| expires_at | absolute expiry |
| payload | serialized/encrypted payload |
| state | local lifecycle state |
| received_at | first local receipt |
| last_forwarded_at | forwarding bookkeeping |

## `message_receipts`

Tracks evidence that this node has already processed a message.

| Field | Purpose |
|---|---|
| message_id | unique message identifier |
| first_seen_at | first observation |
| source_node_id | node that supplied this copy |
| processed | whether forwarding decision completed |

Primary key:

```text
message_id
```

This makes duplicate detection an indexed database operation.

## `neighbors`

| Field | Purpose |
|---|---|
| node_id | peer identity/token |
| last_seen_at | latest observation |
| rssi | recent signal observation |
| connection_success_rate | link quality |
| last_connected_at | latest successful link |
| protocol_version | peer protocol |
| capabilities | peer capabilities |

## `forward_attempts`

Optional but useful for debugging and evaluation.

| Field | Purpose |
|---|---|
| message_id | message |
| peer_node_id | attempted next hop |
| timestamp | attempt time |
| result | success/failure |
| reason | diagnostic reason |

---

# 6. Message lifecycle

```text
CREATED
   |
   v
PERSISTED
   |
   v
QUEUED
   |
   +---- no neighbor ----> WAITING
   |
   v
OFFERED
   |
   v
TRANSFERRED
   |
   v
STORED_BY_NEXT_NODE
   |
   +---- destination? ----> DELIVERED
   |
   +---- relay required ----> QUEUED
   |
   +---- TTL=0 / expired ----> EXPIRED
```

A local node should never delete a message merely because one forwarding attempt failed.

### Suggested states

```text
CREATED
QUEUED
WAITING_FOR_NEIGHBOR
FORWARDING
RELAYED
DELIVERED
EXPIRED
REJECTED
```

A separate `seen` record is preferable to overloading message state for duplicate suppression.

---

# 7. SOS-to-delivery data flow

Example topology:

```text
A ---- B ---- C ---- D
```

A creates SOS for D.

### Step 1 — creation

A generates:

```text
message_id = M123
origin = A
destination = D
TTL = 8
expiry = T + configured lifetime
```

A persists M123.

### Step 2 — discovery

A detects B.

B advertises that it participates in the mesh protocol.

### Step 3 — synchronization

A and B establish a BLE GATT session.

They exchange:

```text
HELLO
INVENTORY_REQUEST
INVENTORY_RESPONSE
```

A learns whether B already has M123.

### Step 4 — forwarding

B does not have M123.

A sends M123.

B verifies:

- protocol version;
- message integrity/authenticity if enabled;
- expiry;
- message ID;
- TTL.

B persists the message before acknowledging successful receipt.

### Step 5 — duplicate prevention

B later meets C.

C may already have M123.

The inventory exchange allows B to avoid retransmitting it.

### Step 6 — continued relay

If C lacks M123:

```text
B -> C -> D
```

At each hop:

```text
receive
validate
deduplicate
persist
decrement TTL
select forwarding candidates
forward
```

### Step 7 — destination

D recognizes:

```text
destination_id == local_node_id
```

D marks M123 delivered locally.

D sends a delivery acknowledgment if the protocol supports return-path acknowledgments.

### Step 8 — reverse delivery

The acknowledgment itself can be a separate mesh message and can travel through different nodes.

Therefore:

> A forward path and an acknowledgment path do not need to be the same path.

---

# 8. Routing model

## 8.1 Do not implement a global shortest-path algorithm initially

A conventional shortest-path protocol assumes reasonably stable topology and route visibility.

Emergency phone meshes may instead have:

- intermittent contacts;
- rapidly changing neighbors;
- devices entering/leaving range;
- battery limitations;
- OS scheduling delays.

The initial design should use **local forwarding utility**.

---

## 8.2 Neighbor utility score

For each candidate peer, calculate a score from locally observable properties:

```text
score(peer, message) =
    w1 * recency
  + w2 * link_success_rate
  + w3 * delivery_history
  + w4 * estimated_progress
  + w5 * peer_availability
  - w6 * congestion
```

Do not claim that this is mathematically optimal.

It is a heuristic for selecting better forwarding opportunities.

---

## 8.3 Store-carry-forward

If no suitable peer exists:

```text
retain message locally
wait
scan/advertise again
re-evaluate neighbors
```

This is essential to support disconnected periods.

---

## 8.4 Dynamic rerouting

Suppose:

```text
A -> B -> C
```

and B disconnects.

A does not treat the entire message as failed.

Instead:

```text
message remains queued
       |
       v
new neighbor discovered
       |
       +--> C
       |
       +--> E
       |
       +--> F
```

The routing engine recomputes the forwarding candidate set from current observations.

This is dynamic rerouting without requiring a globally synchronized route table.

---

# 9. Duplicate prevention

Duplicate suppression is mandatory because multiple paths can converge.

Example:

```text
       B
      / \
A ---     --- D
      \ /
       C
```

A message may arrive at D from both B and C.

### Rule

Every message has an immutable globally unique `message_id`.

Recommended construction:

```text
message_id = random 128-bit value
```

Alternative deterministic construction:

```text
origin_node_id + origin_sequence_number
```

The database enforces uniqueness:

```text
PRIMARY KEY(message_id)
```

Processing logic:

```text
if message_id already exists:
    reject duplicate
else:
    validate
    persist
    enqueue
```

### Important distinction

Duplicate prevention is not the same as loop prevention.

TTL prevents unlimited propagation.

Message ID prevents repeated processing of the same message.

Both are required.

---

# 10. TTL / hop handling

Use two independent expiry mechanisms:

## 10.1 Hop TTL

Example:

```text
TTL = 8
```

On each successful relay:

```text
TTL = TTL - 1
```

If:

```text
TTL == 0
```

do not forward further.

## 10.2 Absolute expiry

Example:

```text
expires_at = creation_time + 30 minutes
```

A node must not forward a message after expiry even if TTL remains.

### Why both?

TTL controls network propagation.

Time expiry controls stale emergency information.

---

# 11. Offline storage behavior

The message must survive:

- temporary BLE absence;
- peer disconnection;
- app process restart, where Android permits the app to resume;
- temporary network partition;
- failed forwarding attempt.

Recommended queue priority:

```text
1. SOS
2. Delivery ACK
3. emergency text
4. normal text
5. control / diagnostic
```

Storage should be bounded.

When storage reaches a configured threshold:

- delete expired messages first;
- then apply explicit retention rules;
- never silently delete active high-priority SOS messages unless the documented hard limit is reached.

For a hackathon prototype, use a configurable maximum queue size and record drops for evaluation.

---

# 12. Connection and transfer protocol

Suggested session:

```text
A discovers B
     |
     v
connect GATT
     |
     v
service discovery
     |
     v
HELLO exchange
     |
     v
version/capability negotiation
     |
     v
inventory exchange
     |
     v
select messages
     |
     v
transfer frames
     |
     v
receiver validates + persists
     |
     v
ACK
     |
     v
sender marks hop successful
```

### Transfer atomicity

A receiver should acknowledge a data message only after durable local persistence.

This avoids:

```text
receive -> ACK -> crash -> message lost
```

---

# 13. BLE framing and chunking

BLE GATT transfers are characteristic-based and should not be treated as an arbitrary byte-stream abstraction.

Therefore:

```text
Application message
       |
       v
Protobuf serialization
       |
       v
Frame encoder
       |
       +--> frame 1
       +--> frame 2
       +--> ...
       |
       v
BLE characteristic writes/notifications
```

Receiver:

```text
BLE fragments
   |
   v
reassembly buffer
   |
   v
frame validation
   |
   v
Protobuf parse
```

Required safeguards:

- maximum frame size;
- maximum reassembly size;
- timeout for incomplete messages;
- sequence number;
- checksum or authenticated integrity;
- reject malformed lengths.

---

# 14. Node states

A node can be modeled as:

```text
DISABLED
  |
  v
INITIALIZING
  |
  v
DISCOVERABLE
  |
  +--> CONNECTING
  |       |
  |       +--> ACTIVE
  |              |
  |              +--> DISCONNECTING
  |                         |
  |                         v
  +---------------------- DISCOVERABLE
```

Operational states:

### `DISABLED`
Bluetooth unavailable, permission denied, or user disabled mesh operation.

### `INITIALIZING`
Loading identity, database, BLE stack and configuration.

### `DISCOVERABLE`
Advertising and/or scanning according to policy.

### `CONNECTING`
GATT connection in progress.

### `ACTIVE`
Neighbor session established and data exchange possible.

### `BACKOFF`
Recent failure detected; retry delayed to avoid connection storms.

### `DEGRADED`
BLE is available but scanning/advertising/connection behavior is restricted.

---

# 15. Failure and recovery

## Peer disappears during transfer

Action:

```text
mark attempt failed
retain message
close connection
increase retry backoff
wait for another contact
```

Do not decrement TTL merely because a connection attempt failed.

TTL should represent successful forwarding hops, not connection attempts.

---

## App process stops

Room persists the queue.

On next valid initialization:

```text
load unexpired queued messages
resume discovery
resume forwarding
```

However, the architecture must not claim guaranteed background execution: Android imposes background restrictions and BLE scanning has power/resource constraints.

---

## Bluetooth disabled

Transition to:

```text
DISABLED
```

Keep messages in Room.

When Bluetooth becomes available, return to discovery.

---

## Destination unavailable

Retain message until:

```text
delivered
OR expired
OR explicit cancellation
```

---

## Malformed message

Reject without forwarding.

Record only a minimal diagnostic reason.

Never allow malformed payloads to crash the mesh service.

---

# 16. Security architecture

Security must be layered.

## Threats

### Eavesdropping

A nearby attacker may observe BLE traffic.

Mitigation:

- application-layer authenticated encryption for message payloads;
- do not rely only on BLE link security.

Android's BLE guidance explicitly recommends app-layer security when sensitive data is handled.

### Message tampering

Attacker changes payload.

Mitigation:

```text
AEAD authentication tag
or
digital signature + encryption
```

### Replay

Attacker retransmits an old SOS.

Mitigation:

- message ID;
- creation timestamp;
- expiration;
- destination validation;
- seen-message database.

### Spam/flooding

Malicious node injects thousands of messages.

Mitigation:

- message size limits;
- per-peer rate limits;
- per-origin quotas;
- TTL;
- queue quotas;
- priority handling.

### Impersonation

Attacker claims to be another node.

Mitigation:

- public-key node identity;
- signed control messages;
- authenticated session handshake.

### Privacy leakage

Even encrypted payloads may reveal metadata:

- timing;
- approximate proximity;
- message size;
- relay participation;
- node presence.

The prototype should document metadata exposure honestly.

---

# 17. Recommended security baseline for the prototype

Minimum acceptable:

```text
message_id
+
expiry
+
TTL
+
authenticated payload
+
replay protection
+
input limits
```

Preferred production direction:

```text
device identity keypair
+
authenticated key agreement
+
AEAD payload encryption
+
signed control metadata
+
key rotation/revocation strategy
```

Do not invent a custom cryptographic algorithm.

Use established Android/JVM cryptographic primitives and libraries after threat-model review.

---

# 18. Android-specific limitations

These are not optional details; they affect whether the architecture works in the field.

## 18.1 BLE range is environment-dependent

Do not promise a fixed range.

Walls, bodies, radio interference, antenna design, transmit power and phone model all affect connectivity.

## 18.2 BLE is optimized for small data transfers

The design should therefore focus on short messages, not arbitrary files.

Android describes BLE as suitable for transferring small amounts of data.

## 18.3 Scanning consumes battery

Android recommends time-limited scanning rather than indefinite loops.

Therefore:

```text
scan window
pause
advertise
connect opportunistically
```

should be considered instead of continuous high-duty scanning.

## 18.4 Background execution is constrained

Android has background execution restrictions and specific BLE guidance.

The system must be tested with:

- screen on/off;
- app foreground/background;
- battery saver;
- Doze;
- different OEM power managers;
- process restart;
- permissions revoked.

## 18.5 BLE role constraints

A useful mesh node may need both:

- central/scanner behavior;
- peripheral/advertising behavior;

and may act as both GATT client and server.

Not every phone model behaves identically under simultaneous workloads.

---

# 19. Realistic limitations

The system cannot guarantee delivery.

The correct statement is:

> The system increases the probability of eventual delivery when a connected sequence of participating nodes exists before message expiry.

Failure cases include:

- no participating relay nodes;
- insufficient node density;
- all relay devices have Bluetooth disabled;
- Android background restrictions;
- battery exhaustion;
- radio interference;
- incompatible device behavior;
- message TTL expiry;
- malicious nodes;
- storage exhaustion.

The network can be partitioned indefinitely.

No routing algorithm can deliver a message across a permanently disconnected graph.

---

# 20. Comparable systems

## Briar

Briar is a decentralized Android messenger that supports direct synchronization over Bluetooth/Wi-Fi and can operate without Internet connectivity. Its documentation describes storing messages and later passing suitable data when contacts come within range. It also explicitly discusses Android background and Bluetooth limitations.

**Similarity:**
- offline communication;
- local storage;
- Bluetooth;
- store-and-forward behavior;
- decentralized architecture.

**Difference for our project:**
- our prototype is explicitly designed as an emergency relay protocol;
- the protocol is intentionally small and focused on SOS delivery;
- routing/forwarding policy is a first-class component;
- the architecture is being built around a defined BLE + Protobuf + Room protocol stack;
- evaluation can directly measure hop count, delivery probability, latency and battery cost.

## Bridgefy

Bridgefy describes an offline messaging system based on Bluetooth Low Energy and multi-hop forwarding.

**Similarity:**
- BLE;
- offline operation;
- multi-hop message propagation.

**Difference:**
- Bridgefy is a mature product/SDK; our project is an inspectable architecture/protocol prototype.
- Our design explicitly separates transport, persistence, duplicate suppression, TTL, routing and security.
- The implementation can expose measurable routing decisions instead of treating the mesh as a black-box SDK.

## Conventional infrastructure messaging

SMS, cellular messaging and Internet messengers normally depend on external infrastructure.

Our architecture deliberately removes the central delivery dependency.

---

# 21. What is technically different about our implementation?

Do not claim that multi-hop BLE messaging itself is novel; it is not.

The defensible contribution is the **specific engineering combination and evaluation**:

1. **Emergency-first forwarding policy**
   - SOS receives highest queue/forwarding priority.

2. **Persistent store-before-forward**
   - every relay persists the message before acknowledging receipt.

3. **Dual expiry**
   - hop TTL + absolute expiry.

4. **Explicit duplicate index**
   - database-enforced message identity.

5. **Adaptive local routing**
   - forwarding score based on observed neighbor behavior.

6. **Failure-tolerant rerouting**
   - no fixed path is required.

7. **Android-aware mesh behavior**
   - scanning, advertising, connection and background restrictions are treated as architectural constraints.

8. **Measurable protocol**
   - Protobuf frames, deterministic state transitions and database records make experiments reproducible.

The technical claim should be:

> We implement and evaluate a lightweight emergency-oriented store-carry-forward protocol over Android BLE, with persistent message state, duplicate suppression, bounded propagation, and adaptive local forwarding.

Do not claim:

- guaranteed delivery;
- city-wide range from a single phone;
- universal Android background operation;
- a novel mesh networking algorithm unless experiments establish one.

---

# 22. Testing strategy

## Unit tests

Test:

- message ID uniqueness;
- TTL decrement;
- expiry;
- duplicate insertion;
- state transitions;
- routing score;
- malformed protobuf;
- queue priority.

## Integration tests

At least:

```text
A -> B
A -> B -> C
A -> B -> C -> D
A -> B
A -> C
B disconnects
duplicate through two paths
TTL reaches zero
message expires
```

## Fault injection

Simulate:

- disconnect during frame;
- duplicate packet;
- corrupted frame;
- delayed node;
- node disappearing;
- database restart;
- Bluetooth disabled;
- permission denied;
- queue full.

## Physical tests

Use several Android devices.

Measure:

- discovery time;
- connection success rate;
- transfer success rate;
- end-to-end delivery latency;
- hop count;
- duplicate rate;
- battery consumption;
- performance with increasing node count;
- performance under mobility.

---

# 23. Evaluation metrics

The hackathon demo should produce numbers, not just screenshots.

### Delivery ratio

```text
delivered_messages / created_messages
```

### End-to-end latency

```text
delivery_time - creation_time
```

### Average hops

```text
sum(hops_per_delivered_message) / delivered_messages
```

### Duplicate suppression ratio

```text
duplicates_detected / total_duplicate_arrivals
```

### Forwarding efficiency

```text
useful_forwarded_messages / total_forward_attempts
```

### Queue survival

Percentage of messages retained until a valid forwarding opportunity.

### Battery cost

Measure battery percentage or energy estimate over a fixed test interval.

---

# 24. Likely judge questions and strong answers

## Q1. Why BLE instead of Wi-Fi?

**Answer:** BLE is available on modern Android devices and is designed for low-power short-range communication. Our messages are small, so we trade throughput for lower power and infrastructure independence. The design does not claim BLE is superior for large data transfer.

## Q2. Is this really a mesh if there is no IP routing?

**Answer:** It is an application-layer opportunistic mesh. Devices discover peers and relay application messages over BLE links. We are not claiming to implement an IP-layer mesh protocol.

## Q3. How do you prevent infinite loops?

**Answer:** Every message has a unique message ID for duplicate suppression and a bounded TTL plus absolute expiry for propagation control.

## Q4. What happens if the next hop disappears?

**Answer:** The message remains durably stored. The routing engine marks the attempt as failed, applies backoff, and chooses another neighbor when one becomes available. No fixed route is required.

## Q5. How do you know a message was actually delivered?

**Answer:** The destination creates a delivery acknowledgment. The acknowledgment is itself a mesh message and can take a different route back to the origin.

## Q6. What happens when two paths deliver the same SOS?

**Answer:** The destination uses the message ID as the deduplication key. Only the first valid copy is processed; later copies are discarded or acknowledged without reprocessing.

## Q7. Why Room instead of an in-memory queue?

**Answer:** Emergency delivery cannot depend on process lifetime. Room gives us durable SQLite-backed state so messages survive temporary disconnection and app restarts.

## Q8. Why Protocol Buffers?

**Answer:** We need a compact, typed binary protocol shared between Kotlin/Java components. Protobuf also supports schema evolution and generated code.

## Q9. Can you guarantee delivery?

**Answer:** No. Delivery requires a connected temporal path of participating devices before message expiry. The system provides store-carry-forward and rerouting to improve delivery probability; it cannot overcome a permanently disconnected network.

## Q10. What is your biggest Android limitation?

**Answer:** The OS controls Bluetooth scanning, background execution and power behavior. A theoretically correct mesh protocol can still fail to discover peers if the OS restricts the application. Therefore our evaluation explicitly tests foreground/background and power-management conditions.

## Q11. Why not use a central server?

**Answer:** The failure scenario assumes infrastructure may be unavailable. A server would reintroduce the dependency the project is designed to remove.

## Q12. Is this technology new?

**Answer:** No. Offline Bluetooth multi-hop messaging has existing examples. Our contribution is a focused emergency protocol architecture combining persistent store-and-forward, duplicate control, bounded TTL, adaptive local forwarding, and Android-aware transport behavior, followed by measurable evaluation.

## Q13. What prevents a malicious phone from flooding the network?

**Answer:** The production design needs authentication, rate limiting, per-origin quotas, size limits, TTL and queue quotas. Security is a required subsystem, not an assumption.

## Q14. Why not use a conventional shortest-path algorithm?

**Answer:** The topology is intermittent and local. A route that exists now may disappear seconds later. A store-carry-forward design can continue operating without a stable end-to-end route.

## Q15. What is the minimum viable demo?

**Answer:**

```text
3–5 Android phones

Phone A: create SOS
Phone B: relay
Phone C: destination

Demonstrate:
A -> B -> C

Then:
disconnect B
introduce another relay
demonstrate rerouting

Then:
send the same message through two paths
demonstrate duplicate suppression
```

---

# 25. Recommended implementation order

### Phase 1 — local persistence

Implement:

- Room schema;
- message lifecycle;
- message IDs;
- TTL;
- expiry;
- queue.

### Phase 2 — one-hop BLE

Implement:

- advertising;
- scanning;
- GATT connection;
- Protobuf framing;
- one-hop send/receive.

### Phase 3 — multi-hop

Implement:

- persist-before-forward;
- duplicate detection;
- TTL decrement;
- relay queue.

### Phase 4 — routing

Implement:

- neighbor table;
- link statistics;
- forwarding score;
- retry/backoff.

### Phase 5 — acknowledgments

Implement:

- end-to-end delivery ACK;
- ACK relay;
- delivery state.

### Phase 6 — security

Implement:

- authenticated encryption;
- identity;
- replay protection;
- rate limits.

### Phase 7 — evaluation

Run:

- controlled topology tests;
- disconnection tests;
- duplicate tests;
- background tests;
- battery tests.

---

# 26. Architecture decisions to keep stable

Unless the lead developer provides evidence requiring change, keep these invariants:

1. **Messages are persisted before forwarding.**
2. **Message IDs are immutable.**
3. **TTL only decreases on successful relay.**
4. **Expiry is independent of TTL.**
5. **Duplicate detection is database-backed.**
6. **No global route is required for basic operation.**
7. **A disconnected peer does not delete the message.**
8. **BLE transport does not own business logic.**
9. **Routing does not directly manipulate SQLite.**
10. **UI does not directly manipulate BLE.**
11. **Security is applied at the application layer for sensitive messages.**
12. **Delivery is probabilistic, not guaranteed.**

---

# 27. Open implementation questions

These must be decided by the lead developer and recorded as Architecture Decision Records (ADRs):

- Minimum Android API level?
- Kotlin only, or Kotlin + Java?
- BLE GATT service UUID?
- Exact characteristic layout?
- Maximum SOS payload size?
- Default TTL?
- Default message expiry?
- Broadcast SOS vs addressed SOS?
- Identity model: random node ID or public-key identity?
- Encryption library?
- ACK format?
- Maximum concurrent GATT sessions?
- Scan duty cycle?
- Advertising duty cycle?
- Queue maximum size?
- Background execution strategy?
- Battery optimization handling?
- OEM devices to support?
- Whether Android-only interoperability is acceptable?
- Whether relay nodes are trusted or untrusted?

---

# 28. Architecture change protocol

When the lead developer supplies a new implementation detail:

1. Update the affected module.
2. Identify changed interfaces.
3. Check impact on:
   - data model;
   - message lifecycle;
   - routing;
   - BLE protocol;
   - security;
   - failure recovery;
   - testing.
4. Record the change in an ADR.

ADR template:

```text
ADR-NNN
Title:
Date:
Status:

Context:
Decision:
Alternatives considered:
Consequences:
Affected modules:
Tests required:
```

This prevents the documentation from becoming inconsistent as implementation evolves.

---

# 29. Current research references

Primary technical references:

- Android Developers — Bluetooth Low Energy overview and GATT behavior.
- Android Developers — BLE scanning guidance.
- Android Developers — BLE background communication.
- Android Developers — Bluetooth permissions.
- Android Developers — Room persistence.
- Protocol Buffers documentation — encoding, schema language and generated APIs.
- Briar Project — offline/decentralized messaging and store-carry-forward behavior.
- Bridgefy — BLE offline multi-hop messaging.

These references should be rechecked before final submission because Android behavior and third-party products can change.

---

# 30. Final technical position

The strongest defensible architecture is:

```text
                         +----------------+
                         |   SOS / UI     |
                         +-------+--------+
                                 |
                         +-------v--------+
                         | Message Service|
                         +-------+--------+
                                 |
                    +------------v-------------+
                    | Persistent Message Queue |
                    |       Room / SQLite      |
                    +------------+-------------+
                                 |
                    +------------v-------------+
                    |   Routing / Relay Engine |
                    | TTL / expiry / duplicate  |
                    | suppression / scoring     |
                    +------------+-------------+
                                 |
                    +------------v-------------+
                    |     BLE Transport        |
                    | Scan / Advertise / GATT  |
                    +------------+-------------+
                                 |
                     ~~~~~ BLE radio links ~~~~
                                 |
                  +--------------+--------------+
                  |                             |
               Node B                        Node C
                  |                             |
             store/relay                    store/relay
                  |                             |
                  +-------------+---------------+
                                |
                              Node D
                           destination
```

The core engineering idea is **persistent, bounded, opportunistic forwarding over short-lived BLE contacts**.

The system is technically credible if the implementation demonstrates the invariants above under real device disconnections, duplicate paths, background restrictions and message expiry—not merely if it can send a message between two phones.
