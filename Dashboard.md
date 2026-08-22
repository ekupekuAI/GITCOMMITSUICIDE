 # Mesh Rescue Network - Dashboard Specification

## 1. Goal

Build a single-screen network operations dashboard that makes the Android mesh behavior obvious to judges:

```text
discover nearby peers -> connect -> forward SOS hop by hop
-> reject duplicates -> survive disconnection -> reroute
-> confirm destination receipt / acknowledgement
```

The topology is the hero view. The message path and event ledger provide proof of what happened.

## 2. Technical Boundaries

This dashboard must visualize events emitted by the real Android peer-to-peer transport and forwarding protocol. It must not invent capabilities.

Allowed only when observed or supplied by the app:

- Peer discovery, connection state, send/receive/forward events, message IDs, hop counts, TTL, duplicate rejection, and acknowledgements.
- Measured link or end-to-end latency, with timestamp quality shown when clocks are not synchronized.
- Application-level forwarding routes.
- Optional battery, signal, transport, or coarse position data only when the app actually exposes it.

Never imply internet access, GPS precision, physical distance, guaranteed delivery, unlimited range, or an AI router. Nearby discovery is not an active connection. A forwarded message is not delivery confirmation.

## 3. Main Layout

```text
┌─────────────────────────────────────────────────────────────────────┐
│ MESH RESCUE NETWORK       LIVE / REPLAY       NETWORK HEALTH        │
├───────────────┬─────────────────────────────────────┬───────────────┤
│ NODE ROSTER    │          NETWORK TOPOLOGY           │ MESSAGE       │
│                │                                     │ INSPECTOR     │
│ Active         │        ● A ─────── ● B              │ SOS-1042      │
│ Nearby         │             ╲       │               │ Path / hops   │
│ Disconnected   │              ● C ── ● D             │ Status        │
│                │            SOS packet               │ Latency       │
├───────────────┴─────────────────────────────────────┴───────────────┤
│ EVENT LEDGER | HOPS | DUPLICATES | ROUTES | DELIVERY | LATENCY      │
└─────────────────────────────────────────────────────────────────────┘
```

Use high-contrast cyan for active traffic, amber for duplicate/degraded states, red for failures, and gray for disconnected or stale nodes. Pair every color with text, icon, or line style.

## 4. Data Contract

### Node

```text
nodeId, label, role, state, lastSeenAt
optional: batteryPercent, transport, coarsePosition
```

### Link

```text
linkId, fromNodeId, toNodeId, state, lastChangedAt
optional: latencyMs, rawSignalMetric, transport
```

### Message

```text
messageId, kind, sourceId, destinationId, createdAt
status, hopCount, ttlRemaining, observedPath
```

### Event

```text
eventId, at, kind, nodeId, peerId, messageId, reason
```

Event kinds should include the real protocol equivalents of:

```text
DISCOVERED, CONNECTED, DISCONNECTED
MESSAGE_CREATED, RECEIVED, FORWARDED, DUPLICATE_REJECTED
ROUTE_FAILED, ROUTE_CHANGED, DESTINATION_RECEIVED, ACK_RECEIVED
```

The frontend reacts to this event stream. It never creates a packet animation without a corresponding event.

## 5. Visualization Specifications

### Connected Nodes

**Data:** Node records, connection events, active links, last-seen timestamps.

**User sees:** Each Android device is a labeled node. Solid lines mean actively connected peers. Active, forwarding, destination, and disconnected states have distinct labels and markers.

**Node fails:** The node becomes `DISCONNECTED`; attached links become broken/dashed or disappear. History remains visible briefly so the cause and impact can be understood.

**Message forwards:** The used link pulses, the receiver highlights, and the hop is added to the selected message.

**Duplicate arrives:** The receiver shows a short amber `DUPLICATE REJECTED` branch. No new route is drawn.

### Nearby Nodes

**Data:** Discovery events, discovery timeout, last discovered time, connection state, and optional raw signal metric.

**User sees:** A dotted discovery ring or `NEARBY / NOT CONNECTED` list. Do not display meters unless a defensible distance estimate is supplied by the actual Android technology.

**Node fails:** The entry ages to `UNKNOWN` and then expires according to the real discovery timeout. Do not infer physical movement.

**Message forwards:** Only an actual forwarding connection animates. An unused nearby peer stays quiet.

**Duplicate arrives:** Topology is unchanged; the event ledger records the rejection.

### Active Connections

**Data:** Link state transitions, connection attempts, transport, and optional measured link latency.

**User sees:** `CONNECTED`, `CONNECTING`, `DEGRADED`, or `DISCONNECTED` labels on links. Active transfer temporarily thickens/highlights the link.

**Node fails:** Show the observed transition and reason, then stop route animations on that edge.

**Message forwards:** Display `TX -> RX`, message ID suffix, and one temporary directional pulse.

**Duplicate arrives:** Show `RX DUP`; do not count it as a successful forward.

### SOS Propagation

**Data:** Message ID, source, destination, timestamps, status, observed path, and TTL only if implemented.

**User sees:** A packet such as `SOS-1042` travels across observed links. Each confirmed forwarding event shows a numbered hop and appears in the ledger.

**Node fails:** The packet stops at the failed edge. The inspector reports `ROUTE FAILED` and the actual reason.

**Message forwards:** Receiver confirmation and forwarding event advance the packet to the next node and increment the hop count.

**Duplicate arrives:** Show a small amber rejected branch labeled with the message ID. The main path and hop count do not change.

### Message Hops

**Data:** Ordered observed forwarding events, per-hop timestamps, hop count, and TTL/max-hop setting when available.

**User sees:** `A -> B -> C -> D`, with `HOP 1`, `HOP 2`, and per-hop durations where measured.

**Node fails:** Mark the interrupted edge with `X` and retain the last confirmed hop.

**Message forwards:** Add exactly one hop after the protocol confirms that forwarding occurred.

**Duplicate arrives:** Add a rejected side event, never a hop.

### Duplicate Rejection

**Data:** Message ID, receiver, first-seen record, duplicate event, reason, and timestamp.

**User sees:** An amber `DUPLICATE REJECTED` panel showing `already processed`, the source peer, and unchanged forward/delivery totals.

**Node fails:** No duplicate metric changes unless an actual duplicate is received.

**Message forwards:** Valid forwarding increments forward/hop totals; it does not increment duplicate totals.

**Duplicate arrives:** Increment `duplicatesRejected`, log the ID, and do not deliver or forward a second copy.

### Node Disconnection and Route Failure

**Data:** Disconnection event, affected links, last successful activity, failed message/edge, and supplied reason.

**User sees:** A clear `NODE OFFLINE` state followed by a red `ROUTE FAILED` edge strike-through and `last confirmed hop` in the inspector.

**Node fails:** Remove it from active route candidates. Never erase the event history.

**Message forwards:** A failed route changes only after an alternate receiver confirms forwarding.

**Duplicate arrives:** Show as a local protocol rejection, not as route failure.

### Automatic Rerouting

**Data:** Failed path, candidate connected peers, route attempt ID, selected next hop, and route result.

**User sees:** The old path dims; an observed alternate path is labeled `ALTERNATE PATH SELECTED` or `REROUTED`. Show the old and new hop ladders.

**Node fails:** Display rerouting only if the real app selects another reachable peer and emits forwarding events. Otherwise display `NO ALTERNATE OBSERVED`.

**Message forwards:** The new path becomes authoritative after its first successful forwarding event, not after discovery alone.

**Duplicate arrives:** It does not trigger rerouting unless the real protocol explicitly does so.

### Delivery Confirmation

**Data:** Destination receipt, acknowledgement event/message if implemented, message ID, path, and timestamps.

**User sees:** `DESTINATION RECEIVED` at the destination. Show `ACK RECEIVED` at the source only when an acknowledgement actually returns. These are separate states.

**Node fails:** Unconfirmed messages remain `UNCONFIRMED`; forwarding alone never becomes delivery.

**Message forwards:** Status remains `FORWARDED` until destination receipt or an explicit delivery confirmation.

**Duplicate arrives:** A second receipt remains `ALREADY RECEIVED` and does not create a second delivery.

### Latency

**Data:** Monotonic send/receive/ack timestamps, per-hop samples, and clock-quality metadata.

**User sees:** End-to-end latency for confirmed messages and per-hop latency where measured. Label sample count; do not present one sample as a stable average.

**Node fails:** Mark affected metrics `STALE`; missing samples are not zero.

**Message forwards:** Add measured hop delay and queue time separately if available.

**Duplicate arrives:** Record duplicate arrival as diagnostic data but exclude it from successful delivery latency.

### Network Health

**Data:** Active nodes/links, fresh discovery state, send/forward failures, confirmed deliveries, duplicate count, route failures, and latency samples.

**User sees:** A transparent breakdown rather than an arbitrary percentage:

```text
ACTIVE NODES     7 / 8
ACTIVE LINKS     9
DELIVERY         2 / 2 confirmed
ROUTE FAILURES   1
DUPLICATES       1 rejected
LATENCY          218 ms, 4 samples
STATUS           DEGRADED
```

**Node fails:** Reachability and stability degrade only from fresh observed events; the affected routes are listed.

**Message forwards:** Successful forwarding shows progress but does not count as delivery.

**Duplicate arrives:** Count it as a protocol-safety event, not a link failure. A high duplicate rate may create a separate `DEDUP PRESSURE` warning.

## 6. Event Ledger

Every animated state has a timestamped textual proof trail:

```text
14:32:00  A             SOS CREATED SOS-1042
14:32:01  A -> B        FORWARDED HOP 1
14:32:02  B -> C        FORWARDED HOP 2
14:32:03  E -> B        DUPLICATE REJECTED SOS-1042
14:32:04  C             DISCONNECTED
14:32:04  B -> C        ROUTE FAILED peer unavailable
14:32:05  B -> E        ALTERNATE PATH SELECTED
14:32:06  E -> D        FORWARDED HOP 3
14:32:07  D             DESTINATION RECEIVED
14:32:08  A             ACK RECEIVED
```

Clicking a node, link, or message filters the ledger and opens its details. `LIVE / REPLAY` uses the same event schema. Reset clears the run ID and all counters.

## 7. Two-Minute Hackathon Demo

Use five labeled Android devices or a controlled bridge that emits the same real app events: A source, B and C relays, D destination, and E alternate relay.

| Time | Action | Visible evidence |
|---|---|---|
| 0:00-0:15 | Show idle network | Connected solid links, nearby dotted peer, node roster, and health breakdown. Explain that nearby is not connected. |
| 0:15-0:30 | Trigger SOS A -> D | `SOS-1042` is created and travels A -> B -> C. Hop markers and ledger events appear. |
| 0:30-0:42 | Confirm D receipt | D shows `DESTINATION RECEIVED`; A shows `ACK RECEIVED` only if implemented. Latency updates. |
| 0:42-0:55 | Send the same message again | B visibly rejects the duplicate. Duplicate count rises; hops and deliveries do not double-count. |
| 0:55-1:10 | Disconnect C | C turns gray, links break, and the original route shows `ROUTE FAILED`. Pause so judges see the failure. |
| 1:10-1:32 | Send a second SOS | If supported, the app selects A -> B -> E -> D. The failed path dims, the alternate path is labeled, and hops resume only on real forwards. |
| 1:32-1:45 | Degrade or delay a link | Show measured latency rising and health moving to `DEGRADED`; do not invent bandwidth or distance. |
| 1:45-1:55 | Confirm delivery | D and, if applicable, A show their separate confirmation states. Compare original and rerouted latency. |
| 1:55-2:00 | Freeze final ledger | Show route failure, reroute, hops, duplicate rejected, active nodes, latency, and confirmed deliveries. |

Suggested closing line:

> The capability is not simply sending an SOS. It is keeping the message moving when the network changes, while showing exactly what was observed.

## 8. Acceptance Checklist

- Every animation has an event, timestamp, message ID, and node/link IDs.
- Discovery is visibly different from an active connection.
- A disconnected node cannot be used without a new observed connection.
- Duplicates never increment hop, forward, or delivery totals.
- Destination receipt and source acknowledgement are separate states.
- Stale, missing, or unsynchronized data is labeled honestly.
- Live and replay modes use the same event schema.
- The two-minute scenario resets and repeats cleanly.
- The dashboard makes no unsupported claim about GPS, internet access, range, guaranteed delivery, or AI routing.
Yes. For a hackathon, I would **not make this look like a normal SOS app**. The dashboard should make the judges immediately understand:

> **“There is no central internet server required for the message to move. Devices discover nearby peers, forward the message hop-by-hop, reject duplicates, recover from broken routes, and report delivery when an acknowledgement actually reaches the origin.”**

Below is a practical dashboard specification designed around that idea.

# Mesh Rescue Network — Dashboard Specification

## 1. Main Dashboard Layout

Use a **single-screen network operations dashboard**.

```text
┌─────────────────────────────────────────────────────────────────────┐
│  MESH RESCUE NETWORK                         ● NETWORK ACTIVE       │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│                    NETWORK TOPOLOGY                                 │
│                                                                     │
│        NODE-04 ●────────● NODE-07                                  │
│                  ╲      ╱                                          │
│                   ╲    ╱                                           │
│              NODE-05 ●──────● NODE-08                              │
│                       │                                             │
│                       │                                             │
│                 🚨 NODE-01                                         │
│                    SOS                                               │
│                                                                     │
│ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌───────────────┐  │
│ │  NODES      │ │ ACTIVE HOPS │ │ LATENCY     │ │ NETWORK       │  │
│ │     8       │ │     4       │ │   142 ms    │ │ HEALTH 92%    │  │
│ └─────────────┘ └─────────────┘ └─────────────┘ └───────────────┘  │
│                                                                     │
├───────────────────────────┬─────────────────────────────────────────┤
│ MESSAGE FLOW              │ EVENT LOG                               │
│                           │                                         │
│ SOS #1042                 │ 14:32:01 NODE-01 → NODE-03             │
│ NODE-01 → 03 → 07 → 08   │ 14:32:02 NODE-03 forwarded             │
│ Hops: 3                   │ 14:32:03 duplicate from NODE-05        │
│ Status: DELIVERED ✓       │ 14:32:04 NODE-07 → NODE-08              │
│                           │ 14:32:05 ACK received ✓                  │
└───────────────────────────┴─────────────────────────────────────────┘
```

The most important visual is the **network topology in the center**.

---

# 2. Visualization #1 — Connected Nodes

### Purpose

Show the actual devices participating in the mesh.

### Data required

Each node should have:

```text
nodeId
status
batteryLevel
lastSeen
connectionCount
```

Example:

```text
NODE-01
Battery: 78%
Connections: 3
```

### What the user sees

Each Android device is represented by a node.

* Active node → solid circle
* Disconnected node → grey/dimmed
* SOS-origin node → SOS indicator
* Forwarding node → temporary animation
* Destination/acknowledgement → delivery indicator

Connections are lines between nodes.

**Important:** only show connections that the actual Android implementation knows about. Don't draw an arbitrary "mesh" merely for appearance.

### When a node fails

Suppose:

```text
NODE-07
```

disconnects.

Its node becomes:

```text
NODE-07  ×
OFFLINE
```

Connections attached to NODE-07 disappear or become broken.

### When a message is forwarded

The connection being used becomes highlighted temporarily:

```text
NODE-03 ───────► NODE-07
```

The animation should show the message moving along the connection.

### When a duplicate is received

Do **not** create another message animation through the network.

Instead:

```text
NODE-05 → NODE-03

DUPLICATE
Message ID: SOS-1042
Already processed
```

This visually demonstrates that the network isn't blindly forwarding every copy.

---

# 3. Visualization #2 — Nearby Nodes

This is different from "connected nodes."

A device may **discover a nearby peer without that peer currently being part of the active route**.

### Data required

```text
nodeId
discovery status
approximate proximity / signal information
last discovered time
connection status
```

Only expose distance if the underlying Android technology actually provides a defensible distance estimate.

Otherwise use:

```text
Nearby
Discovered
Not connected
```

rather than inventing:

```text
12.4 metres
```

### What the user sees

Use a subtle discovery ring around the selected node.

Example:

```text
        NODE-06
       ◌ nearby

   NODE-03 ●────────● NODE-07
```

Different visual states:

* **Connected**
* **Nearby / discovered**
* **Unknown / no longer seen**

### Node failure

If NODE-07 disappears:

```text
NODE-07
OFFLINE
```

Nearby nodes may remain visible if they are still discovered.

### Message forwarded

Only the **actual forwarding connection** should animate.

Nearby-but-unused nodes should **not** appear as if they forwarded the message.

### Duplicate

No topology change.

The event log should show:

```text
DUPLICATE IGNORED
SOS-1042
NODE-05
```

---

# 4. Visualization #3 — Active Connections

Make active connections visually different from passive discovery.

### Data

```text
sourceNode
destinationNode
connectionState
lastActivity
transport type (only if actually known)
```

Possible states:

```text
CONNECTED
CONNECTING
DISCONNECTED
FAILED
```

### Visual

Normal:

```text
NODE-03 ───────── NODE-07
```

Active message transfer:

```text
NODE-03 ═══════► NODE-07
```

The thicker/highlighted connection lasts only while the event occurs.

### Node failure

Connection changes:

```text
NODE-03 ───────X NODE-07
```

Then disappears if the connection is actually gone.

### Forwarding

Animate:

```text
SOS ● ─────────►
```

along the connection.

### Duplicate

The connection itself does not change.

Only the event/message layer changes.

---

# 5. Visualization #4 — SOS Message Propagation

This should be the **hero visualization**.

Imagine the SOS starts at:

```text
NODE-01
```

Then:

```text
NODE-01
   ↓
NODE-03
   ↓
NODE-07
   ↓
NODE-08
```

Display:

```text
SOS #1042

NODE-01 → NODE-03 → NODE-07 → NODE-08
```

### Data

Every message needs a unique identifier.

For example:

```text
messageId = SOS-1042
originNode = NODE-01
timestamp
payload
hopCount
TTL / expiry if implemented
status
```

The exact fields depend on your actual protocol.

### What judges see

A message packet travels from node to node.

At every hop:

```text
HOP 1
NODE-01 → NODE-03

HOP 2
NODE-03 → NODE-07

HOP 3
NODE-07 → NODE-08
```

This makes the mesh behavior obvious.

---

# 6. Visualization #5 — Message Hops

Show a **Hop Counter** prominently.

```text
CURRENT HOP

        3
      / 5 max
```

And below:

```text
PATH

NODE-01
   ↓
NODE-03       HOP 1
   ↓
NODE-07       HOP 2
   ↓
NODE-08       HOP 3
```

### Data

```text
messageId
currentNode
previousNode
hopCount
path/history
```

Only display a complete path if your protocol actually records it.

Otherwise display the observed forwarding sequence from dashboard events.

### Node failure

If the next node fails:

```text
NODE-07
   X
```

Then show:

```text
ROUTE FAILED
Attempting alternate route...
```

### Forwarding

Increment:

```text
HOP 2 → HOP 3
```

### Duplicate

**Do not increment the hop counter for a rejected duplicate.**

This is a very important visual detail.

---

# 7. Visualization #6 — Duplicate Message Rejection

This could be one of your strongest hackathon features.

Suppose:

```text
NODE-03
```

already processed:

```text
SOS-1042
```

Then NODE-05 receives the same message and tries to send it.

Dashboard:

```text
┌─────────────────────────────┐
│ DUPLICATE MESSAGE           │
│                             │
│ SOS-1042                    │
│ Received from NODE-05       │
│                             │
│ Already processed           │
│                             │
│        REJECTED ✓           │
└─────────────────────────────┘
```

### Data

Your actual protocol needs some mechanism such as:

```text
messageId
processed-message cache / set
```

Do not claim a particular implementation unless your developer actually implements it.

### Node failure

No direct effect.

### Forwarded message

The original message continues normally.

### Duplicate

This is where the dashboard changes most:

```text
DUPLICATE → REJECTED
```

And importantly:

```text
Hop count: unchanged
Forward count: unchanged
```

This proves the duplicate was not propagated again.

---

# 8. Visualization #7 — Node Disconnection

Give the dashboard a very obvious state transition.

### Normal

```text
NODE-07 ●
ACTIVE
```

### Failure

```text
NODE-07 ×
DISCONNECTED
```

Connections:

```text
NODE-03 ─────X NODE-07
NODE-07 ─────X NODE-08
```

### Data

```text
nodeId
connection state
last-seen timestamp
disconnect event
```

### Message behavior

If no alternative route exists:

```text
ROUTE FAILED
MESSAGE NOT DELIVERED
```

If another route exists:

```text
ROUTE FAILED
ALTERNATE ROUTE FOUND
```

This distinction is crucial.

**Do not display "automatic rerouting" if the actual implementation does not perform it.**

---

# 9. Visualization #8 — Route Failure

This should be an explicit event, not just a dead connection.

Example:

Initial:

```text
NODE-01
   ↓
NODE-03
   ↓
NODE-07
   ↓
NODE-08
```

NODE-07 disconnects.

Show:

```text
✕ ROUTE FAILURE

NODE-03 → NODE-07

Reason:
Peer unavailable / connection lost
```

Then pause for perhaps 0.5–1 second.

That pause makes the failure understandable to judges.

---

# 10. Visualization #9 — Automatic Rerouting

If your actual mesh protocol supports discovering/choosing another reachable path, show:

```text
ROUTE FAILURE

NODE-03 ──X── NODE-07

Searching available peers...

        ↓

ALTERNATE ROUTE

NODE-03 → NODE-05 → NODE-08
```

Then the SOS packet continues:

```text
SOS ●
      NODE-03
          ↓
      NODE-05
          ↓
      NODE-08 ✓
```

### Data required

At minimum:

```text
current node
available peers
failed connection
selected next hop
route attempt
route result
```

### Very important technical rule

Don't call this:

> "AI-powered intelligent routing"

unless you actually implemented AI.

Call it:

> **Automatic alternate-path selection**

That is much more credible.

---

# 11. Visualization #10 — Message Delivery Confirmation

When the destination receives the SOS, don't immediately say:

```text
DELIVERED
```

Instead show what your actual system can verify.

For example, if you implement an acknowledgement:

```text
SOS → NODE-08

NODE-08 received SOS

ACK ← NODE-08
      ↓
NODE-05
      ↓
NODE-03
      ↓
NODE-01

✓ DELIVERY CONFIRMED
```

### Data

Potentially:

```text
messageId
acknowledgementId
destination
acknowledgement path
timestamp
```

Only show an ACK if your protocol genuinely sends one.

Otherwise use:

```text
DESTINATION RECEIVED
```

rather than falsely claiming end-to-end delivery confirmation.

---

# 12. Visualization #11 — Network Latency

Don't make this a fancy meaningless graph.

Use actual timestamps from your system.

Example:

```text
MESSAGE LATENCY

Origin → Destination

142 ms
```

And optionally:

```text
Hop 1      41 ms
Hop 2      38 ms
Hop 3      63 ms
───────────────
Total     142 ms
```

Only show per-hop latency if you actually measure it.

### Node failure

Display:

```text
142 ms
↓
Route changed
↓
218 ms
```

This makes the effect of rerouting visible.

### Duplicate

A duplicate should be recorded separately:

```text
Duplicate received: +57 ms
Action: rejected
```

Don't include rejected duplicates in successful delivery latency unless clearly labeled.

---

# 13. Visualization #12 — Network Health

Create a compact health panel:

```text
NETWORK HEALTH

█████████░  92%

Nodes       8 / 9 active
Connections 11
Routes      3 available
Latency     142 ms
Messages    14
Duplicates  3 rejected
```

### Suggested health calculation

Don't create an arbitrary "92%" unless you define how it is calculated.

For a hackathon prototype, either:

**Option A — component metrics**

```text
ACTIVE NODES: 8/9
ACTIVE LINKS: 11
RECENT DELIVERY: OK
LATENCY: 142ms
```

or define a transparent health score based on measurable factors.

I recommend **Option A** because it is more technically defensible.

---

# 14. Event Timeline

This is extremely important for judges.

Every important network event appears chronologically.

Example:

```text
14:32:00  NODE-01    SOS CREATED
14:32:01  NODE-01 → NODE-03    FORWARDED
14:32:01  NODE-03    MESSAGE ACCEPTED
14:32:02  NODE-03 → NODE-07    FORWARDED
14:32:03  NODE-05 → NODE-03    DUPLICATE REJECTED
14:32:04  NODE-07    DISCONNECTED
14:32:04  NODE-03 → NODE-07    ROUTE FAILED
14:32:05  NODE-03 → NODE-05    ALTERNATE ROUTE
14:32:06  NODE-05 → NODE-08    FORWARDED
14:32:07  NODE-08    MESSAGE RECEIVED
14:32:08  NODE-01    ACK RECEIVED
```

This gives judges a **proof trail**.

---

# 15. Dashboard Data Model

The frontend developer should receive events rather than hard-code animations.

A conceptual event stream could look like:

```text
NODE_DISCOVERED
NODE_CONNECTED
NODE_DISCONNECTED

MESSAGE_CREATED
MESSAGE_RECEIVED
MESSAGE_FORWARDED
MESSAGE_DUPLICATE
MESSAGE_REJECTED

ROUTE_FAILED
ROUTE_CHANGED

DELIVERY_CONFIRMED
ACK_RECEIVED
```

The actual API/event names are for the backend developer to decide.

The key principle is:

> **The visualization reacts to real network events. It should never simulate an event that didn't happen.**

---

# 16. Recommended Visual Language

Keep it simple.

| State             | Visual treatment                 |
| ----------------- | -------------------------------- |
| Active node       | Bright solid node                |
| Nearby node       | Thin ring                        |
| Connected link    | Normal line                      |
| Active forwarding | Animated/highlighted line        |
| SOS origin        | SOS marker                       |
| Message           | Moving packet                    |
| Duplicate         | Short red/orange rejection event |
| Failed route      | Broken line                      |
| Disconnected node | Grey/dim node + X                |
| Rerouted path     | Newly highlighted path           |
| Delivered         | Checkmark                        |
| Waiting           | Pulsing/neutral state            |

Don't use 15 different colors. Judges need to understand the system in seconds.

---

# 17. Most Important Design Principle

The dashboard should have **three layers**:

### Layer 1 — Physical/network topology

```text
Which devices can communicate?
```

### Layer 2 — Message propagation

```text
Where is SOS-1042 going?
```

### Layer 3 — Network events

```text
Why did the message take this route?
```

This prevents the dashboard from becoming a pretty animation with no technical meaning.

---

# 18. Two-Minute Hackathon Demonstration

I would structure the demo like this.

## 0:00–0:15 — Establish the network

Show 6–8 Android devices/nodes.

Dashboard:

```text
● NODE-01
● NODE-02
● NODE-03
● NODE-04
● NODE-05
● NODE-06

NETWORK ACTIVE
```

Say:

> "These are independent devices forming a local communication network. The important part is that communication between nearby devices doesn't depend on every device having internet access."

Don't claim that the entire system is internet-free unless your actual transport and implementation support that.

---

## 0:15–0:35 — Create SOS

Trigger SOS from NODE-01.

Dashboard immediately shows:

```text
🚨 SOS-1042

NODE-01
   ↓
NODE-03
   ↓
NODE-06
```

Animate the message packet.

At the same time:

```text
HOP 1
HOP 2
```

Event log:

```text
SOS CREATED
FORWARDED
FORWARDED
```

### Judge should understand:

**The message is moving device-to-device.**

---

# 19. 0:35–0:50 — Demonstrate Duplicate Rejection

Have another path cause the same message to reach a node that already processed it.

Show:

```text
NODE-05 → NODE-03

SOS-1042
DUPLICATE

REJECTED ✓
```

The key visual:

```text
Forwarded:  ✓
Duplicate:  ✕
```

Say:

> "Because every SOS has an identifier, a node can recognize that it has already processed this message instead of forwarding the same SOS repeatedly."

Only say this if the actual implementation has that mechanism.

This is a **much better technical demonstration than simply showing an SOS notification.**

---

# 20. 0:50–1:10 — Kill a Node

Disconnect:

```text
NODE-06
```

Dashboard changes:

```text
NODE-06
× DISCONNECTED
```

Then:

```text
ROUTE FAILED
```

The original route visibly breaks.

Pause briefly.

This is important.

Don't immediately jump to the new route.

Let judges see:

> **The system actually encountered a failure.**

---

# 21. 1:10–1:30 — Automatic Rerouting

Now show:

```text
SEARCHING ALTERNATIVE...
```

Then:

```text
NEW ROUTE

NODE-01
   ↓
NODE-03
   ↓
NODE-05
   ↓
NODE-08
```

Animate the SOS along the new route.

Event log:

```text
ROUTE FAILED
ALTERNATE PATH SELECTED
MESSAGE FORWARDED
```

This should be the **biggest visual moment of the demo**.

---

# 22. 1:30–1:45 — Delivery Confirmation

Destination:

```text
NODE-08
```

receives the message.

Show:

```text
✓ SOS RECEIVED
```

If your protocol has an acknowledgement:

```text
ACK → NODE-01
```

Then:

```text
✓ DELIVERY CONFIRMED
```

Show:

```text
Total hops: 4
Latency: 218 ms
Route changes: 1
Duplicates rejected: 1
```

Now the judges can see measurable results.

---

# 23. 1:45–2:00 — Final Dashboard Summary

Freeze the topology.

Show:

```text
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

SOS-1042        ✓ DELIVERED

Original route  FAILED
Alternate route SUCCESSFUL

Hops            4
Duplicates      1 REJECTED
Nodes active    7 / 8
Latency         218 ms
Route changes   1

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

Then give the final line:

> **"The key capability isn't simply sending an SOS. It's keeping the message moving when the network itself changes."**

That is a much stronger hackathon ending.

---

# 24. What NOT to Put in the Dashboard

Avoid features that could make judges question the technical credibility.

### ❌ Don't show

```text
GPS accuracy: 1.2m
```

unless you actually have that accuracy.

### ❌ Don't show

```text
NODE-04 is 17.3 meters away
```

unless your technology can legitimately estimate that.

### ❌ Don't show

```text
Internet: OFF
Message delivered globally
```

if the implementation doesn't actually support that.

### ❌ Don't claim

> "Unlimited range."

Mesh networks don't magically provide unlimited range.

### ❌ Don't claim

> "AI automatically finds the best route."

unless you actually implemented an AI routing system.

### ❌ Don't fake packet animations.

The animation should correspond to actual events.

---

# 25. The One Thing That Will Impress Judges

Don't try to impress them with 30 charts.

Make **one complete causal chain** visible:

```text
SOS CREATED
     ↓
MESSAGE FORWARDED
     ↓
HOP 1
     ↓
HOP 2
     ↓
DUPLICATE RECEIVED
     ↓
DUPLICATE REJECTED
     ↓
NODE FAILS
     ↓
ROUTE FAILS
     ↓
ALTERNATE ROUTE FOUND
     ↓
MESSAGE FORWARDED
     ↓
DESTINATION RECEIVES
     ↓
ACK / DELIVERY CONFIRMED
```

And simultaneously, the topology should visually reflect every event.

That gives the judges something much stronger than a conventional SOS dashboard:

**they can actually see the distributed system behaving.**

### Recommended dashboard priority

If your frontend time is limited, implement these first:

1. **Live network topology**
2. **SOS packet animation**
3. **Hop counter/path**
4. **Node failure**
5. **Route failure**
6. **Rerouting**
7. **Duplicate rejection**
8. **Delivery/ACK**
9. **Event timeline**
10. Latency/network-health metrics

The first **8** are the core demonstration. Latency and health are supporting evidence, not the main attraction.
