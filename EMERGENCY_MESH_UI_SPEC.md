# Emergency Communication Mesh
## Android UI/UX Design Specification

**Audience:** Lead Android developer and Jetpack Compose implementation team  
**Product posture:** Mission-critical, fast to scan, usable offline, explicit about uncertainty  
**Primary users:** Field responders, incident coordinators, and people requesting help

---

## 1. Product Principles

1. **The next action is always obvious.** Every operational screen has one dominant action and a visible back path.
2. **Truth before reassurance.** Show `Unknown`, `Stale`, `Failed`, and `Offline` explicitly. Never imply delivery when only local storage succeeded.
3. **Severity is semantic, not decorative.** Color is paired with text, icon, and position so alerts remain understandable for color-blind users.
4. **Local data remains useful.** Cached nodes, alerts, and messages remain visible with an age label when disconnected.
5. **Prevent accidental SOS.** Sending an SOS is deliberate, but once confirmed it must be fast and resilient.
6. **Battery is an operational constraint.** Surface battery and power-saving mode without blocking emergency use.

---

## 2. Visual Direction

### Color tokens

Use Material 3 tokens with a restrained, high-contrast palette. Do not use color as the only status signal.

| Token | Light value | Meaning |
|---|---|---|
| `emergencyCritical` | `#B3261E` | Immediate danger, active SOS, failed critical delivery |
| `emergencyHigh` | `#C75B12` | Urgent response required |
| `emergencyModerate` | `#8A6500` | Important but not immediately life-threatening |
| `statusOnline` | `#176B3A` | Confirmed connected / delivered |
| `statusDegraded` | `#8A6500` | Partial, stale, or uncertain connectivity |
| `statusOffline` | `#5F6368` | No upstream path / device offline |
| `surfaceBase` | `#F7F8F6` | App background |
| `surfaceRaised` | `#FFFFFF` | Tool panels and list rows |
| `inkPrimary` | `#1A1C19` | Main text |
| `inkSecondary` | `#454744` | Supporting text |
| `outline` | `#747873` | Dividers and controls |

Critical SOS banners use a solid critical token with white text, not a gradient. Critical actions use a filled button; destructive cancellation uses an outlined button with clear wording.

### Typography

Use a legible, non-condensed sans family available on the device, with tabular numerals for times, hop counts, and battery percentages. Suggested Material roles:

- `headlineSmall`: screen title, 24sp
- `titleMedium`: alert title and section title, 16sp medium
- `bodyLarge`: primary operational copy, 16sp
- `bodyMedium`: supporting copy, 14sp
- `labelLarge`: buttons and status labels, 14sp medium
- `labelSmall`: timestamps and metadata, 12sp

Minimum touch target: 48dp. Prefer short labels such as `Send SOS`, `Acknowledge`, `Retry`, and `Open map`. Avoid icon-only controls unless the icon is universally understood and has a tooltip/content description.

### Surfaces and motion

- Use flat, high-contrast list rows with 8dp corner radius; avoid nested cards and decorative illustrations.
- Keep the top app bar compact and persistent on operational screens.
- Use a short fade/slide for newly received alerts and a restrained pulse only for an unacknowledged critical SOS.
- Respect `Reduced motion`; replace animation with a static `NEW` label.
- Never hide an alert or status change behind animation.

---

## 3. Navigation Structure

Use a `NavigationSuiteScaffold` or adaptive equivalent:

### Primary destinations

1. **Dashboard** - operational overview and dominant emergency actions
2. **Alerts** - received and created SOS alerts
3. **Messages** - mesh message queue and delivery details
4. **Network** - node list, topology, and local device status

On compact screens these are a bottom navigation bar. On expanded screens use a navigation rail with labels. Preserve the selected destination across rotation and process recreation.

### Secondary routes

- Dashboard -> `Create SOS`
- Dashboard / Alerts -> `Alert detail`
- Messages -> `Message detail`
- Network -> `Node detail`
- Network -> `Topology`
- Any screen -> `Settings and power`

A persistent global critical alert strip appears below the top app bar when an unacknowledged critical SOS exists. Tapping it opens the highest-priority alert detail.

---

## 4. Emergency Alert Hierarchy

Every alert has a severity, lifecycle, source, confidence, and delivery state.

### Severity order

1. **Critical / Red:** immediate threat to life; requires rapid acknowledgement
2. **High / Orange:** urgent assistance required
3. **Moderate / Amber:** assistance needed, not immediately life-threatening
4. **Low / Gray:** information or welfare check

### Alert precedence

`Critical unacknowledged` > `Critical acknowledged` > `High unacknowledged` > `High acknowledged` > `Moderate` > `Low`.

A critical alert must show: severity word, source identifier, relative age, location confidence, delivery confidence, and current acknowledgement state. Use an icon plus text: triangle/exclamation for critical, not just a red dot.

### Lifecycle labels

`Draft` -> `Queued locally` -> `Relaying` -> `Delivered` -> `Acknowledged` -> `Resolved`  
Failure branches: `Retrying`, `No route`, `Expired`, `Delivery uncertain`.

---

## 5. Dashboard Screen

### Purpose
Provide a fast operational readout and direct access to SOS creation, active alerts, network health, and the responder workload.

### Components

- Compact top app bar: app name, local node ID, settings icon
- Persistent offline/degraded banner when applicable
- Critical alert strip with count and highest-priority alert
- **Primary SOS action:** full-width `Send SOS` button
- Current node status row: node ID, role (`Responder`, `Requester`, `Relay`), battery, last sync
- Network summary: reachable nodes, active routes, relay queue, last topology update
- Message summary: queued, relaying, delivered, failed
- Received SOS preview list, sorted by hierarchy
- Responder queue preview: `Needs acknowledgement`, `Assigned to me`, `Nearby`
- Last updated label for every cached summary

### Buttons/actions

- `Send SOS`
- `View all alerts`
- `Open network`
- `Open messages`
- Tap any alert preview to open detail
- Tap node status to open local node status

### Important information

- Whether this device can currently relay an emergency message
- Whether the local node has a route to another node or gateway
- Number of unacknowledged alerts
- Battery percentage and estimated operating mode
- Age of network data; use `Updated 14s ago`, not an unlabeled spinner

### Emergency states

- **Critical alert present:** critical strip is pinned directly under the app bar; dashboard content remains usable.
- **SOS currently active:** primary action changes to `View active SOS`; show status and elapsed time.
- **Low battery:** persistent amber battery row with `Power settings` action; do not disable SOS.
- **Multiple critical alerts:** show count and highest-priority preview; do not rotate content automatically.

### Failure states

- Network query failed: retain cached values and show `Network data unavailable` with `Retry`.
- Dashboard sync failed: show exact age of data and `Last confirmed` timestamp.
- Local storage unavailable: show a blocking error only for affected actions, with `Try again`.

### Empty states

- No active alerts: `No active SOS alerts` plus current network state.
- No messages: `No messages in queue`.
- No nearby nodes: `No nodes heard recently`; show scan action on the Network destination.

---

## 6. Create SOS Screen

### Purpose
Let a person create a valid emergency request in seconds, including enough information for responders even when connectivity is intermittent.

### Components

- Top app bar with `Cancel`
- Severity selector: Critical, High, Moderate, Low; Critical selected by default only when configured by product policy
- Situation type selector: Medical, Fire, Trapped, Missing person, Security, Other
- Location block: coordinates or `Location unavailable`, accuracy, timestamp, permission state
- Optional short description input with character count
- People affected stepper: 1+ or `Unknown`
- Optional responder callback/contact field
- Attachment control only if offline-safe and size-limited
- Delivery preview: local save, expected route, and current node battery
- Full-width `Review SOS` action

### Buttons/actions

- `Review SOS`
- `Use current location`
- `Add description`
- `Remove attachment`
- `Cancel` with discard confirmation if fields changed

### Important information

- The SOS can be stored locally even without internet.
- Location includes accuracy and capture age.
- The user must distinguish `Saved on this device` from `Delivered to responders`.

### Emergency states

- Critical selection shows a clear confirmation warning and requires an explicit final send.
- If an active SOS already exists, show it and offer `Update active SOS` rather than creating an accidental duplicate.
- If location is stale, label it `Last location: 8 min ago` and allow send.

### Failure states

- Location permission denied: show manual location option and `Location unavailable`.
- Required fields invalid: inline error adjacent to field; preserve entered values.
- Attachment too large or unsupported: explain the limit and allow SOS without it.

### Empty states

- No location: structured empty block with `Use current location` and manual coordinates/place input.
- No description: acceptable; do not treat optional description as an error.

---

## 7. SOS Review and Send Screen

### Purpose
Give the sender one final, auditable confirmation before creating the emergency event.

### Components

- Severity and situation summary
- Location, accuracy, and captured time
- Description and affected-person count
- Delivery route preview: `Direct`, `2 hops`, `No route currently known`
- Local persistence statement
- Full-width `Send SOS` button
- Secondary `Edit` action

### Buttons/actions

- `Send SOS`
- `Edit`
- `Cancel`

### Important information

The send result must immediately state one of:

- `Saved and relaying`
- `Saved locally - no route`
- `Delivered to node [ID]`
- `Delivery uncertain - retrying`

### Emergency states

After tapping send, disable duplicate submission, show an immediate local event ID, and keep the user on a status screen. The UI must remain useful if the process loses connectivity during submission.

### Failure states

Never show a generic success toast. A local database write failure is a hard failure with `Try again`; a network failure after local write is `Saved locally` with retry status.

### Empty states

Not applicable after review data exists. If state is lost, restore the draft or show `No SOS draft found` with `Return to dashboard`.

---

## 8. Active SOS Status Screen

### Purpose
Show the sender exactly what happened to their SOS and whether responders have acknowledged it.

### Components

- Large status word and severity
- Event ID and creation time
- Delivery progress timeline: Created, Queued, Relaying, Delivered, Acknowledged
- Hop count and last relay node
- Location and location age
- Responder acknowledgement area
- Update/cancel controls subject to policy
- Retry route action when available

### Buttons/actions

- `Retry now`
- `Update SOS`
- `Cancel SOS` with typed/explicit confirmation
- `View relay path`

### Important information

Display both `last attempt` and `last confirmed` times. Show `Acknowledged by [node/responder]` only when an acknowledgement packet is actually received.

### Emergency states

- Critical unacknowledged: persistent alert treatment and visible elapsed time.
- Delivered but not acknowledged: clear distinction; delivery is not responder receipt.
- Acknowledged: show acknowledgement source and time.

### Failure states

- No route: `Stored locally. No route found.` Keep retry available.
- Relay timeout: show last known hop and `Route stalled`.
- Duplicate update conflict: preserve existing SOS and ask user to retry the update.

### Empty states

- No relay details yet: `Waiting for first relay attempt`.
- No acknowledgement: `No acknowledgement received` is a valid state, not an error.

---

## 9. Received SOS Alerts Screen

### Purpose
Give responders a prioritized, filterable queue of incoming emergency events.

### Components

- Top app bar with unread count and filter control
- Segmented filter: All, Unacknowledged, Nearby, Assigned to me
- Sort control: severity first, newest, distance
- Alert rows containing severity, situation, source ID, age, distance/location confidence, delivery state, acknowledgement state
- Pull-to-refresh or explicit refresh that works with cached data
- Offline banner and data age labels

### Buttons/actions

- Tap row: open alert detail
- `Acknowledge` from row for configured roles
- `Assign to me` from row where supported
- `Filter`
- `Mark reviewed` for non-critical alerts

### Important information

A row must be scannable in under two seconds: severity word, event ID or source, age, location confidence, and whether another responder acknowledged it.

### Emergency states

New critical rows appear at the top and use a one-time visual entrance cue. Keep them pinned until acknowledged or resolved.

### Failure states

- Sync failure: preserve cached list with `Last received ...`.
- Malformed alert: show `Unreadable alert [ID]`; retain raw packet access for diagnostics if permitted.
- Duplicate alert: group by event ID and show relay count, not multiple competing rows.

### Empty states

- No alerts: `No SOS alerts received` plus last sync time.
- No results for filter: `No unacknowledged alerts` with `Clear filter`.

---

## 10. Alert Detail / Responder View

### Purpose
Support the responder's complete workflow from assessment through acknowledgement, assignment, relay, and resolution.

### Components

- Severity header with event lifecycle state
- Source node, event ID, creation age, and last packet time
- Situation description and affected-person count
- Location map/coordinate panel with accuracy ring and stale indicator
- Relay path summary: hop count, last known relay, route confidence
- Responder action bar
- Activity timeline: received, relayed, acknowledged, assigned, updated
- Related messages and attachments
- Resolution field and audit history

### Buttons/actions

- `Acknowledge`
- `Assign to me`
- `Relay alert`
- `Send update`
- `Open location`
- `Mark resolved`
- `Report incorrect`

### Important information

Acknowledging means the responder has seen the alert, not that help has arrived. Assignment and resolution are separate actions. Every action shows its local timestamp and delivery state.

### Emergency states

- Critical: acknowledge action is dominant; unresolved critical alerts retain the critical header.
- Assigned: show responder identity and assignment time.
- Resolved: retain the full audit trail; do not erase the alert.

### Failure states

- Cannot acknowledge upstream: save acknowledgement locally and show `Acknowledgement queued`.
- Cannot relay: show `Relay failed`, route reason if known, and `Retry`.
- Location unavailable: preserve coordinates/source text and show confidence as `Unknown`.

### Empty states

- No description: show `No description provided`.
- No activity yet: show receipt event only.
- No route data: show `Relay path unavailable`.

---

## 11. Messages Screen

### Purpose
Expose the mesh message queue and make delivery uncertainty understandable.

### Components

- Tabs: Outbox, Inbox, Failed
- Message rows: type, event ID, priority, status, hop count, attempts, age
- Status filter
- Retry controls for failed messages
- Queue capacity indicator

### Buttons/actions

- Tap row: message detail
- `Retry failed`
- `Clear delivered` with confirmation
- `Filter`

### Important information

Use explicit statuses: `Queued locally`, `Relaying`, `Delivered to next node`, `Delivered`, `Failed`, `Expired`, `Unknown`. `Delivered to next node` is not the same as final delivery.

### Emergency states

Critical messages are pinned above normal messages and cannot be bulk-cleared while unresolved.

### Failure states

Queue full: show capacity and require a policy decision; never silently discard critical messages. Corrupt packet: isolate it and expose event ID.

### Empty states

Each tab gets its own message, such as `No failed messages` or `Outbox is empty`.

---

## 12. Message Detail / Relay-Hop Information

### Purpose
Provide technical evidence for delivery decisions without overwhelming the responder.

### Components

- Event/message ID and packet type
- Current delivery status
- Attempt timeline with timestamps
- Hop table: sequence, node ID, received time, forwarded time, RSSI/link quality when available
- Route confidence and expiry
- Raw diagnostic details behind an expandable section

### Buttons/actions

- `Retry`
- `Copy event ID`
- `Open node`
- `Report delivery issue`

### Important information

Use a compact timeline first; the hop table is secondary. Show missing hop information as `Not reported`, never as zero.

### Emergency states

Critical packets use a pinned severity header and retain retry priority.

### Failure states

No route, TTL expired, duplicate packet, invalid signature, and queue rejection each need a distinct reason label and next action where possible.

### Empty states

- No hops reported: `This message has not reached another node.`
- No diagnostics: hide the diagnostics section rather than showing an empty panel.

---

## 13. Network Screen

### Purpose
Show the current node's health, nearby peers, route quality, and mesh availability.

### Components

- Local node status header: ID, role, online/offline, battery, power mode, last beacon
- Network summary: nearby nodes, reachable nodes, gateway availability, route freshness
- Nearby nodes list: node ID, role, distance/signal if available, last seen, relay capability, battery share if permitted
- `Topology` entry point
- Scan/refresh action
- Connection failure summary

### Buttons/actions

- `Scan now`
- Tap node: open node detail
- `Open topology`
- `Retry connection`
- `Power settings`

### Important information

Separate `heard nearby` from `reachable for delivery`. A node can be visible but not a viable route.

### Emergency states

If the local node is the only reachable node, show `Isolated mesh` and relay responsibility. Keep SOS creation available.

### Failure states

- Radio unavailable: identify permission/hardware state and offer settings.
- Scan timeout: retain previous nodes with ages.
- Gateway unavailable: show mesh-local operation separately from internet/gateway state.

### Empty states

- No nearby nodes: show scan result, last scan time, and offline capability.
- No route: `No delivery path currently known`.

---

## 14. Current Node Status Screen

### Purpose
Give a precise health readout for the device acting as a mesh node.

### Components

- Node ID and QR/share identifier where policy permits
- Role and relay mode
- Radio state and permissions
- Battery percentage, charging state, estimated runtime, power saver state
- Storage and queue capacity
- Last beacon and last successful relay
- Clock/time-sync confidence
- Diagnostics list

### Buttons/actions

- `Copy node ID`
- `Toggle relay mode` where authorized
- `Run diagnostics`
- `Power settings`
- `View logs` for authorized technical users

### Important information

Battery `Unknown` is different from 0%. Show whether background restrictions may prevent relaying.

### Emergency states

Low battery may suggest power saving but must not silently stop active SOS handling. Active critical relay work gets a visible priority warning.

### Failure states

Sensor or radio readings unavailable: show the failed subsystem and last known time. Do not present stale battery/network readings as current.

### Empty states

- No diagnostics run: show `Diagnostics not run` with `Run diagnostics`.
- No relay history: show `No relay activity recorded`.

---

## 15. Node Detail Screen

### Purpose
Help responders evaluate whether a nearby node is trustworthy and useful as a relay.

### Components

- Node ID, role, last seen, freshness
- Reachability and route quality
- Signal/link history when available
- Relay capability and queue hints
- Recent interactions and failed attempts
- Approximate location only when privacy policy allows

### Buttons/actions

- `Route test` for authorized users
- `Use as relay` only if protocol supports explicit selection
- `Report node issue`
- `Open topology`

### Important information

Distinguish observed facts from estimates: `Last seen 12s ago` versus `Estimated 2 hops away`.

### Emergency states

A node carrying a critical route is marked `Critical path` with reason and freshness.

### Failure states

Stale node: show last seen age and disable route test if too old. Untrusted/invalid node: explain why it is excluded from routing.

### Empty states

- No signal history: `No link history available`.
- No recent messages: `No recent interactions`.

---

## 16. Topology Screen

### Purpose
Make mesh structure and route fragility understandable at a glance.

### Topology visualization concept

Use an interactive 2D graph with the local node centered or highlighted. Nodes are circles with text labels; links are lines. Avoid geographic assumptions unless coordinates are known.

- Local node: thick outline and `This device` label
- Reachable route: solid line
- Degraded/uncertain route: dashed line
- Failed or expired route: muted line with failure label on selection
- Critical SOS path: thicker critical-colored line plus `SOS route` label
- Node fill encodes role, while status icon/text encodes health
- Tap a node or edge to show details in a bottom sheet
- Pinch zoom, pan, reset view, and list fallback are required
- Maximum visible graph should be bounded; cluster or list overflow nodes
- Provide an accessible linear alternative: ordered node and edge list

### Buttons/actions

- `Reset view`
- `List view`
- `Refresh topology`
- `Show critical routes`
- Tap node/edge for details

### Important information

Show topology freshness prominently. A graph from 10 minutes ago must not look live. Include legend and route confidence.

### Emergency states

Critical routes are visually prominent but do not obscure other nodes. A broken critical path must trigger a clear textual banner: `Critical SOS route interrupted`.

### Failure states

No topology data: use the list fallback with `No topology snapshot available`. Partial graph: render known nodes and label missing links as unknown.

### Empty states

`No neighboring nodes have been observed` with local node status and scan action.

---

## 17. Settings and Power Screen

### Purpose
Control operational permissions and expose constraints that affect mesh behavior.

### Components

- Node identity and role
- Relay mode toggle
- Radio/permission status
- Notification and critical-alert override status
- Battery optimization status
- Storage/queue policy
- Data retention and privacy controls
- Diagnostics and app version

### Buttons/actions

- `Open system settings`
- `Run diagnostics`
- `Export diagnostics` where authorized
- `Reset local data` with strong confirmation

### Important information

Explain consequences beside settings: disabling relay mode affects nearby users. Use toggles for binary settings and dialogs for destructive actions.

### Emergency states

Critical alert override status must be visible. System restrictions must be described as restrictions, not silently worked around.

### Failure states

If a setting cannot be changed, show the owning system restriction and a direct settings action.

### Empty states

No diagnostics history: offer `Run diagnostics`.

---

## 18. Compose Implementation Notes

Represent screen data with immutable UI models and explicit state enums. Suggested shared states:

```kotlin
sealed interface ConnectivityState {
    data object Online : ConnectivityState
    data object MeshOnly : ConnectivityState
    data object Offline : ConnectivityState
    data class Stale(val lastConfirmedAt: Instant) : ConnectivityState
    data class Failed(val message: String) : ConnectivityState
}

enum class DeliveryState {
    LocalOnly, Queued, Relaying, NextNodeDelivered,
    Delivered, Acknowledged, Retrying, NoRoute, Expired, Uncertain
}

enum class Severity { Critical, High, Moderate, Low }
```

Implementation requirements:

- Use `Scaffold` with a top app bar and snackbar host; critical state belongs in persistent content, not snackbar-only messaging.
- Keep event IDs stable and make list rows key by event/message/node ID.
- Hoist filter, sort, and selection state to the route-level ViewModel.
- Persist drafts and locally queued SOS packets before attempting transport.
- Use `contentDescription` and semantic labels for severity, delivery status, graph nodes, and all icon buttons.
- Support large font sizes, dark theme, high contrast, TalkBack, and reduced motion.
- Use a list fallback for topology and a text alternative for every graph-only fact.
- Treat stale timestamps as first-class data in every repository response.
- Keep retry idempotent by event ID; never create a second SOS because a transport request timed out.

---

## 19. Cross-Screen Failure Language

Use consistent wording:

- `Saved locally` means local persistence succeeded.
- `Queued for relay` means a transport attempt has not completed.
- `Delivered to next node` means one hop confirmed, not final delivery.
- `Delivered` means the protocol's final delivery condition was confirmed.
- `Acknowledged` means a responder or receiving node explicitly acknowledged it.
- `Delivery uncertain` means the outcome cannot be proven.
- `No route currently known` is not the same as `Failed`.
- `Stale` always includes the last confirmed time.

Avoid `Success`, `Connected`, or `Synced` without a subject and timestamp.

---

## 20. Validation Checklist

Before release, verify:

- A user can create and locally save an SOS with no network.
- Duplicate sends cannot create duplicate event IDs.
- Every critical alert is visible from every primary destination.
- Offline, mesh-only, stale, no-route, and battery-low states are testable in previews.
- A responder can acknowledge, assign, relay, and resolve an alert with explicit status feedback.
- Hop information distinguishes observed, missing, and estimated data.
- Topology has zoom/pan, freshness, critical-route emphasis, and an accessible list fallback.
- All empty states provide a useful next action or a clear explanation.
- Dark theme, large text, TalkBack labels, and color-blind interpretation preserve severity and delivery meaning.

---

## 21. Technical Limitation Refinement Protocol

When implementation constraints arrive, preserve the user-facing guarantees in this order:

1. SOS local persistence and idempotency
2. Clear severity and lifecycle status
3. Offline and stale-state honesty
4. Responder acknowledgement and audit trail
5. Nearby-node and route visibility
6. Topology animation and secondary diagnostics

If a platform or protocol limitation prevents a feature, replace it with an explicit state and a next action. For example, if live topology is unavailable, show the last snapshot age and a list of confirmed nodes; do not render a static graph that appears live.

---

## 22. Screen State Matrix

Every route should support these states through previews and automated UI tests. Loading must never replace already-known operational data.

| Route | Normal | Offline/degraded | Failure | Empty | Critical override |
|---|---|---|---|---|---|
| Dashboard | Live summary | Cached summary with age | Affected summary row has retry | No alerts/messages/nodes | Pinned critical strip |
| Create SOS | Editable form | Local-only delivery preview | Field or storage error | Missing location block | Critical confirmation |
| Active SOS | Timeline and route | Queued/no-route status | Retry with reason | No hop/acknowledgement yet | Persistent critical status |
| Alerts | Prioritized queue | Cached queue with last sync | Sync retry | Filter-specific empty result | Critical rows pinned |
| Alert detail | Responder actions | Actions queued locally | Action-specific failure | Missing description/location | Acknowledge dominant |
| Messages | Queue and statuses | Mesh-only status | Retry or expiry reason | Empty tab | Critical packets pinned |
| Network | Nodes and routes | Last-seen ages | Radio/scan error | No nearby nodes | Isolated mesh warning |
| Topology | Fresh graph | Stale graph with timestamp | Partial/list fallback | No snapshot | Critical path emphasis |

### Shared state presentation

- **Loading:** skeleton only for content with no cached value; otherwise show cached content and a small refresh indicator.
- **Offline:** a persistent, dismissible banner may be dismissed visually, but the state remains in the screen model.
- **Stale:** show the age next to the affected value, not only in a global banner.
- **Failure:** state what failed and provide the narrowest relevant action, such as `Retry scan` rather than a generic `Retry`.
- **Empty:** explain whether there is no data, no matching data, or no route, then provide one useful next action.

---

## 23. Compose Component Map

Build shared primitives before assembling screens. Components should accept semantic state, not infer emergency meaning from arbitrary colors.

| Component | Responsibility | Required inputs |
|---|---|---|
| `ConnectivityBanner` | Offline, mesh-only, stale, and failure messaging | state, lastConfirmedAt, action |
| `SeverityBadge` | Severity icon, label, and accessible description | severity |
| `DeliveryStatusChip` | Human-readable packet state | deliveryState, timestamp |
| `AlertListItem` | Fast scanning of an SOS event | alert summary, action callbacks |
| `NodeStatusRow` | Nearby/local node health | node summary, freshness |
| `OperationalMetric` | Label/value/age presentation | label, value, status, updatedAt |
| `EventTimeline` | Lifecycle and responder audit history | events, currentState |
| `SosActionButton` | Consistent high-priority SOS entry point | activeSos, enabled, onClick |
| `RetryAction` | Narrow retry affordance with progress state | operation, isRetrying, onRetry |
| `TopologyCanvas` | Graph rendering and touch interaction | nodes, edges, selectedItem |
| `TopologyListFallback` | Accessible graph alternative | nodes, edges, routeState |

### Component rules

- `SeverityBadge` always renders text and an icon; do not expose color alone.
- `DeliveryStatusChip` must distinguish `NextNodeDelivered` from `Delivered`.
- `AlertListItem` owns no navigation decision; expose callbacks for acknowledge, assign, and open.
- `SosActionButton` is the only primary dashboard action with emergency styling.
- All reusable components need previews for normal, offline, stale, failure, empty, and critical states.

---

## 24. Interaction Priority Model

When several actions compete for attention, use this order:

1. Protect life: create SOS, acknowledge critical SOS, or restore a critical route.
2. Preserve truth: expose uncertainty, stale data, or a failed delivery attempt.
3. Maintain continuity: retry, relay, or queue work locally.
4. Coordinate response: assign, update, and resolve alerts.
5. Diagnose: inspect hops, topology, logs, and power details.

This order applies to layout, focus, notification priority, and keyboard/accessibility traversal. A diagnostic control must never displace an unacknowledged critical alert.

### Destructive and irreversible actions

- `Cancel SOS` requires a confirmation dialog that repeats the event ID and current delivery state.
- `Clear delivered` never clears unresolved or unacknowledged critical events.
- `Reset local data` requires explicit confirmation and explains that queued offline messages may be lost.
- `Mark resolved` asks for a resolution note when the responder role permits it and retains the audit event.

---

## 25. Notification and Background Behavior

- New critical SOS: high-priority notification with severity, event ID, and `Open alert` action.
- New high/moderate SOS: standard notification grouped by incident.
- Delivery failure: notify only when a user action can help or when a critical event becomes uncertain.
- Repeated relay attempts must update one notification rather than create a notification storm.
- Notification text must not claim final delivery when only local queuing succeeded.
- Tapping a notification opens the alert detail and preserves the global critical strip until acknowledgement.

If background execution is restricted, show the restriction in Current Node Status and Settings. The UI must state when relay behavior is limited by the operating system.

---

## 26. Design Review Questions for Technical Refinement

When the lead developer supplies constraints, resolve these questions explicitly:

- What protocol event proves `Delivered` versus `NextNodeDelivered`?
- Can the local database commit before radio transmission begins?
- What is the maximum packet size and attachment policy offline?
- How long is a node considered fresh, stale, or expired?
- Which roles can acknowledge, assign, relay, resolve, or inspect diagnostics?
- Which location data is safe to expose to nearby nodes and responders?
- What happens to critical packets when the queue is full?
- Which system permissions or background limits can interrupt relaying?

Record each answer in the state model and update the affected screen rows, actions, and failure copy together.
