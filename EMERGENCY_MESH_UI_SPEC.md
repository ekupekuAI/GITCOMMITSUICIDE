Emergency Communication Mesh — Android UI/UX Design Specification
Document Type: UI/UX Design Specification  
Platform: Android  
Implementation Target: Jetpack Compose  
Project: Infrastructure-Independent Emergency Communication Mesh  
Audience: Lead Developer / Android Engineering Team  
Design Priority: Clarity → Speed → Reliability → Situational Awareness → Accessibility
---
1. Design Goals
The application is an emergency-response interface, not a conventional CRUD application.
The UI must:
Make SOS creation possible within seconds.
Keep emergency state visible at all times.
Work clearly when internet/cloud connectivity is unavailable.
Show the health of the local mesh network.
Distinguish confirmed delivery from attempted or failed delivery.
Surface nearby nodes and relay/hop information.
Make severity immediately recognizable.
Avoid decorative elements that reduce readability during stressful situations.
Remain usable in bright outdoor conditions and low-light environments.
Provide a responder-oriented view for receiving and managing SOS incidents.
Communicate uncertainty honestly: unknown, pending, failed, and confirmed must never look identical.
Core UX principle
> **The user should understand “Am I connected?”, “Who can hear me?”, “Did my SOS reach anyone?”, and “What should I do next?” without navigating through multiple screens.**
---
2. Information Architecture
Primary Navigation
Use a bottom navigation structure with four primary destinations:
Dashboard
Mesh
Alerts
Responder
A persistent SOS action remains accessible from the Dashboard and can also be reached from the global emergency action area.
Navigation hierarchy
```text
App
├── Dashboard
│   ├── SOS Creation
│   ├── SOS Active / Tracking
│   ├── Node Status
│   ├── Connection Status
│   └── Battery Status
│
├── Mesh
│   ├── Nearby Nodes
│   ├── Network Health
│   ├── Topology
│   ├── Relay/Hop Details
│   └── Node Details
│
├── Alerts
│   ├── Received SOS Alerts
│   ├── Alert Details
│   └── Message/Delivery Status
│
└── Responder
    ├── Active Incidents
    ├── Incident Details
    ├── Route/Relay Information
    └── Response Status
```
---
3. Global UI Rules
Persistent Status Bar
Every primary screen should expose a compact status area containing:
Mesh status
Node status
Offline/online state
Battery percentage
Active SOS indicator, when applicable
Example:
```text
[ MESH: CONNECTED ] [ NODE: ACTIVE ] [ OFFLINE MODE ] [ 78% ]
```
The exact information can collapse on smaller screens, but emergency state must remain visible.
---
4. Screen Specifications
Screen 1 — Emergency Dashboard
Purpose
Provide a rapid overview of the user's emergency communication state.
This is the main operational screen.
Components
A. Emergency Status Header
Displays:
Current operational state
Node ID / short identifier
Mesh connectivity
Offline mode
Battery level
Example:
```text
NODE ACTIVE
Mesh: CONNECTED
Internet: OFFLINE
Battery: 78%
```
B. SOS Primary Action
Large high-visibility emergency button.
```text
┌────────────────────────────┐
│                            │
│          SEND SOS          │
│                            │
└────────────────────────────┘
```
The action should require deliberate activation to prevent accidental triggering, while remaining extremely fast.
C. Current SOS Status Card
Only visible when an SOS exists.
States:
Not active
Preparing
Broadcasting
Relaying
Acknowledged
Failed
Resolved
D. Network Summary
Display:
Nearby node count
Reachable node count
Active relays
Estimated mesh reachability
Last successful transmission
E. Battery Awareness
Display:
Battery percentage
Battery-saving recommendation when critically low
Whether battery optimization is affecting mesh operation
Buttons / Actions
Send SOS
View SOS Status
Open Mesh
View Alerts
Open Responder View, when applicable
Important Information
Priority order:
Active emergency
SOS delivery state
Mesh availability
Nearby nodes
Battery
Secondary information
Emergency States
Normal
```text
READY
Mesh available
No active emergency
```
SOS Active
Use a persistent emergency banner:
```text
SOS ACTIVE
Broadcasting through mesh
2 relays detected
```
SOS Acknowledged
```text
SOS ACKNOWLEDGED
Responder received alert
```
Critical
```text
SOS DELIVERY AT RISK
No reachable relay currently detected
```
Failure States
No Mesh Connectivity
```text
NO MESH CONNECTION
Your node cannot currently reach another node.
Keep the application open and move toward other devices if possible.
```
Transmission Failure
```text
MESSAGE DELIVERY FAILED
Retrying through available nodes...
```
Empty States
No active SOS:
```text
No active emergency
Your node is ready for emergency communication.
```
---
Screen 2 — SOS Creation
Purpose
Allow the user to create and broadcast an emergency SOS quickly.
Components
Emergency Severity Selector
Levels:
Critical
High
Medium
Low
Recommended default:
High
Critical should require deliberate selection.
Optional Message
Allow a short emergency message.
Example:
```text
Need medical assistance near checkpoint 4.
```
Location Information
Display available location information:
```text
Location: Available
Accuracy: ±18 m
```
If unavailable:
```text
Location unavailable
SOS can still be transmitted.
```
Device Information
Optional responder-relevant information:
Node ID
Battery
Timestamp
Buttons / Actions
Primary:
BROADCAST SOS
Secondary:
Cancel
Important Information
Before transmission, show a compact confirmation:
```text
Severity: HIGH
Location: Available
Mesh: 3 reachable nodes
Battery: 64%
```
Emergency States
Broadcasting
Disable duplicate SOS creation and show:
```text
BROADCASTING SOS...
Searching for relay nodes
```
Broadcast Complete
```text
SOS SENT
Waiting for acknowledgement
```
Failure States
```text
Unable to reach another node.
SOS will continue retrying when a relay becomes available.
```
Empty States
No optional message/location should not block SOS transmission.
---
Screen 3 — Active SOS Tracking
Purpose
Show exactly what is happening after an SOS is created.
Components
SOS Timeline
```text
SOS CREATED
   ↓
LOCAL BROADCAST
   ↓
NODE 12 RELAY
   ↓
NODE 07 RELAY
   ↓
RESPONDER ACKNOWLEDGED
```
Delivery Status
Use explicit states:
Created
Queued
Broadcasting
Relayed
Delivered
Acknowledged
Failed
Hop Information
Display:
```text
Hops: 3
Last relay: NODE-07
Last transmission: 14 sec ago
```
Retry Status
```text
Next retry: Automatic
```
Buttons / Actions
View Network Path
Cancel SOS, if policy allows
Send Update
View Alert Details
Important Information
The distinction between:
Delivered
and
Acknowledged
must be obvious.
A delivered message means it reached a destination/device.
An acknowledged message means the receiving side confirmed receipt.
Emergency States
Active SOS should remain visually dominant.
Failure States
If a relay disappears:
```text
RELAY LOST
Searching for alternate path...
```
If no route exists:
```text
NO ROUTE AVAILABLE
SOS remains queued locally.
```
Empty States
Not applicable while an SOS is active.
---
Screen 4 — Mesh / Network Status
Purpose
Provide technical and operational visibility into the mesh.
Components
Network Health Summary
Display:
Mesh state
Active nodes
Reachable nodes
Relay nodes
Failed links
Last synchronization/transmission time
Example:
```text
MESH HEALTH
GOOD

Active nodes: 12
Reachable: 9
Relays: 4
Failed links: 1
```
Connectivity Modes
Clearly distinguish:
Internet connected
Internet unavailable
Mesh connected
Mesh isolated
Example:
```text
Internet: OFFLINE
Mesh: ACTIVE
```
Buttons / Actions
View Topology
Nearby Nodes
Refresh
Network Diagnostics
Emergency States
If an SOS is active, show the route used by the SOS prominently.
Failure States
```text
MESH DEGRADED

Several relay paths are unavailable.
```
Empty States
```text
NO NEARBY NODES

No mesh peers detected.
```
---
Screen 5 — Nearby Nodes
Purpose
Show devices/nodes that can potentially participate in the emergency mesh.
Components
Each node card should contain:
```text
NODE-07
Signal: Strong
Distance: ~45 m
Role: Relay
Battery: 81%
Status: Reachable
Last seen: 3 sec ago
```
Possible roles:
User
Relay
Responder
Gateway
Buttons / Actions
View Node
Refresh
View on Topology
Important Information
Prioritize:
Reachability
Signal quality
Distance
Role
Battery
Last seen
Emergency States
Highlight nodes currently carrying an active SOS.
Example:
```text
NODE-07
ACTIVE SOS RELAY
```
Failure States
A previously visible node may become:
```text
UNREACHABLE
Last seen: 47 sec ago
```
Do not silently remove recently lost nodes; temporarily show the failure state.
Empty States
```text
NO NEARBY NODES DETECTED

The device is currently isolated from the mesh.
```
---
Screen 6 — Mesh Topology Visualization
Purpose
Provide a visual representation of how nodes are connected.
Topology Concept
Use a graph/network visualization.
```text
             NODE-04
                |
                |
NODE-12 ---- NODE-07 ---- RESPONDER
   |            |
   |            |
NODE-09      NODE-03
```
Visual Encoding
Nodes:
User node
Relay node
Responder node
Gateway node
Connections:
Healthy
Weak
Failed
Active SOS route
Active SOS Route
When an SOS is active, emphasize:
```text
USER → NODE-12 → NODE-07 → RESPONDER
```
Only the active route should receive strong visual emphasis.
Buttons / Actions
Center on My Node
Show SOS Path
Node Details
Zoom
Reset View
Important Information
The visualization should answer:
> “How can my emergency message reach a responder?”
It should not attempt to become a complicated network engineering dashboard.
Failure States
Show broken links explicitly:
```text
NODE-07   X   NODE-03
       LINK FAILED
```
Empty States
If there are no peers:
```text
ISOLATED NODE
No topology available.
```
---
Screen 7 — Node Details
Purpose
Provide detailed information about a selected mesh node.
Components
Node ID
Node role
Reachability
Approximate distance
Signal quality
Battery
Last seen
Current relay activity
Messages relayed
Connection history
Buttons / Actions
View Path
Use as Relay, if system policy permits
Refresh
Back
Emergency States
If the node is currently relaying an SOS:
```text
ACTIVE EMERGENCY RELAY
```
Failure States
```text
NODE UNREACHABLE
Last seen: 2 min ago
```
Empty States
Unavailable metrics should show:
```text
UNKNOWN
```
Never display fake values.
---
Screen 8 — Message Status
Purpose
Provide delivery information for emergency and normal mesh messages.
Components
Message list with status indicators:
```text
SOS — HIGH
14:32
ACKNOWLEDGED

Medical update
14:34
RELAYED — 3 HOPS

Location update
14:35
QUEUED
```
Status Model
Use these canonical states:
```text
DRAFT
QUEUED
BROADCASTING
RELAYED
DELIVERED
ACKNOWLEDGED
FAILED
EXPIRED
```
Buttons / Actions
Open Message
Retry
View Route
View Failure Reason
Failure States
Example:
```text
FAILED
No reachable relay
Retry automatically when connectivity returns.
```
Empty States
```text
NO MESSAGES

Emergency and mesh communication history will appear here.
```
---
Screen 9 — Received SOS Alerts
Purpose
Allow nearby responders/users to see emergency alerts received through the mesh.
Alert Card
Each alert should contain:
```text
CRITICAL
SOS — NODE-12

Medical emergency
Location available
Received 18 sec ago

3 hops
2 relay nodes
```
Alert Priority
Sort by:
Severity
Recency
Distance
Unresolved status
Buttons / Actions
Open Alert
Acknowledge
Respond
View Location
View Relay Path
Emergency States
Critical
Immediate visual priority.
High
Strong but less dominant than Critical.
Medium
Normal alert emphasis.
Low
Normal informational emphasis.
Failure States
If alert information is incomplete:
```text
LOCATION UNKNOWN
MESSAGE RECEIVED
```
If relay information is uncertain:
```text
ROUTE UNKNOWN
```
Empty States
```text
NO ACTIVE SOS ALERTS

No unresolved emergency alerts have been received.
```
---
Screen 10 — SOS Alert Details
Purpose
Give a responder enough information to understand and act on an emergency.
Components
Emergency Summary
```text
CRITICAL
Medical Emergency

Node: NODE-12
Received: 14:32
Status: UNRESOLVED
```
Location
Show:
Location availability
Accuracy
Last updated time
Communication Path
```text
NODE-12
   ↓
NODE-09
   ↓
NODE-07
   ↓
RESPONDER
```
Message
Display the user's emergency description.
Battery
Show sender battery if transmitted.
Last Contact
```text
Last contact: 12 sec ago
```
Buttons / Actions
Primary:
ACKNOWLEDGE SOS
Secondary:
RESPOND
VIEW LOCATION
VIEW PATH
SEND MESSAGE
Emergency States
```text
UNRESOLVED
ACKNOWLEDGED
RESPONDER ASSIGNED
RESPONDER EN ROUTE
RESOLVED
```
Failure States
```text
SENDER NO LONGER REACHABLE

The alert was received, but the sender cannot currently be contacted.
```
Empty States
Not applicable when an alert is opened.
---
Screen 11 — Responder Dashboard
Purpose
Provide a dedicated operational interface for people responding to emergencies.
Components
Active Incident Counter
```text
CRITICAL: 2
HIGH: 4
MEDIUM: 3
```
Incident List
Each incident displays:
Severity
Time
Approximate location
Distance
Last contact
Network confidence
Response status
Responder Status
```text
RESPONDER ACTIVE
Mesh connected
Battery: 82%
```
Buttons / Actions
Open Incident
Set Available / Unavailable
View Mesh
Refresh Incidents
Emergency States
Critical incidents must appear first.
Failure States
```text
NETWORK DEGRADED
Incident updates may be delayed.
```
Empty States
```text
NO ACTIVE INCIDENTS

No unresolved emergencies currently require response.
```
---
Screen 12 — Connection Failure / Diagnostics
Purpose
Explain communication failures and provide actionable recovery information.
Components
Current Failure
Example:
```text
CONNECTION FAILURE

No reachable mesh peers detected.
```
Diagnostic Checklist
```text
✓ Bluetooth enabled
✓ Nearby-device permission granted
✓ Mesh service running
✗ No nearby peers detected
```
Recovery Suggestions
Move closer to other devices.
Keep mesh service running.
Check required device permissions.
Keep battery-saving restrictions from stopping the mesh service, when applicable.
Buttons / Actions
Retry Connection
Run Diagnostics
Open Device Settings
View Nearby Nodes
Emergency States
If SOS is active:
```text
SOS STILL ACTIVE
The system will continue attempting transmission.
```
Failure States
Use precise failure reasons whenever technically available.
Avoid generic:
```text
Something went wrong.
```
Prefer:
```text
No reachable relay nodes detected.
```
Empty States
Not applicable.
---
Screen 13 — Offline Mode
Purpose
Make it clear that lack of internet does not necessarily mean emergency communication has stopped.
Components
Persistent offline banner:
```text
OFFLINE MODE
Internet unavailable
Mesh communication ACTIVE
```
Capabilities
Show what is still available:
```text
✓ Nearby node discovery
✓ Mesh messaging
✓ SOS broadcasting
✓ Relay communication

✗ Cloud synchronization
✗ Internet map tiles
```
Buttons / Actions
View Mesh
View Queued Messages
Diagnostics
Important Information
The UI must distinguish:
Internet unavailable
from:
Mesh unavailable
These are different operational conditions.
---
Screen 14 — Battery Awareness
Purpose
Keep users informed when battery limitations could affect emergency communication.
Components
Battery Card
```text
BATTERY
42%

Estimated risk: LOW
Mesh service: ACTIVE
```
Battery Levels
Suggested states:
Battery	UI State
50–100%	Normal
20–49%	Attention
10–19%	Warning
0–9%	Critical
Buttons / Actions
Enable Battery Saver, if appropriate
View Battery Guidance
Open Battery Settings
Emergency States
When SOS is active:
```text
SOS ACTIVE
Battery: 8%
Minimize non-essential activity.
```
Failure States
If the operating system restricts background communication:
```text
BACKGROUND ACTIVITY RESTRICTED
Mesh reliability may be reduced.
```
Empty States
Not applicable.
---
5. Emergency Alert Hierarchy
The UI must use a consistent severity hierarchy.
Level 1 — CRITICAL
Examples:
Life-threatening emergency
Immediate medical danger
Major disaster
UI behavior:
Highest visual priority
Persistent alert where appropriate
Appears at top of responder queues
Strong vibration/sound may be used according to system policy
Level 2 — HIGH
Examples:
Serious injury
Immediate assistance required
UI behavior:
Strong alert treatment
High queue priority
Level 3 — MEDIUM
Examples:
Assistance required but not immediately life-threatening
Level 4 — LOW
Examples:
Information request
Non-urgent assistance
Important rule
Color must never be the only severity indicator.
Always combine:
Severity label
Icon
Text
Position
Optional color
---
6. Color Guidance
The visual system should be restrained and operational.
Base Colors
Use:
Dark neutral background for command/operational screens where appropriate.
Light neutral surfaces for high-readability content.
White or near-white primary text on dark surfaces.
Dark text on light surfaces.
Semantic Colors
Recommended semantic mapping:
Meaning	Suggested Color Family
Critical / Emergency	Red
High / Warning	Orange
Medium / Attention	Amber
Normal / Healthy	Green
Information	Blue
Unknown	Gray
Offline	Neutral gray
Failed	Red
Pending	Amber/Yellow
Acknowledged	Green
Use colors consistently rather than decorating the UI.
Accessibility
Do not rely on color alone.
For example:
```text
🔴 CRITICAL
⚠ HIGH
● MEDIUM
ℹ LOW
```
The exact iconography can be finalized by the Android developer/design system.
---
7. Typography Guidance
Typography should prioritize fast scanning.
Recommended hierarchy
Display / Emergency
Large, bold text for:
SOS
Critical alerts
Active emergency status
Screen Title
Large/bold.
Example:
```text
Mesh Status
```
Section Title
Medium/bold.
Example:
```text
Nearby Nodes
```
Body
Normal-weight readable text.
Supporting Metadata
Smaller text for:
timestamps
hop count
last seen
battery
signal
Rules
Avoid excessive font weights.
Avoid all-caps for long sentences.
Use all-caps selectively for short emergency labels.
Maintain strong contrast.
Support Android dynamic font scaling.
---
8. Dashboard Layout
Recommended structure:
```text
┌─────────────────────────────────┐
│ NODE ACTIVE       MESH CONNECTED│
│ Offline          Battery 78%    │
├─────────────────────────────────┤
│                                 │
│          SEND SOS                │
│                                 │
├─────────────────────────────────┤
│ CURRENT STATUS                   │
│ No active emergency              │
├─────────────────────────────────┤
│ MESH                             │
│ 12 nodes • 9 reachable • 4 relay│
├─────────────────────────────────┤
│ RECENT ALERTS                    │
│ Critical SOS — 12 sec ago       │
│ High SOS — 2 min ago            │
├─────────────────────────────────┤
│ Dashboard Mesh Alerts Responder │
└─────────────────────────────────┘
```
The SOS action should be visually dominant without making the rest of the dashboard difficult to use.
---
9. Navigation Behavior
Bottom Navigation
Recommended:
```text
[Dashboard] [Mesh] [Alerts] [Responder]
```
Do not place every function in bottom navigation.
Secondary functions should be accessed through their relevant screens.
Back Navigation
Follow standard Android navigation behavior.
An active SOS should never be accidentally cancelled by pressing Back.
Deep Links
The architecture should allow navigation directly to:
Active SOS
SOS alert
Incident
Node details
Message details
---
10. Emergency State Model
The UI should use a consistent state machine.
```text
IDLE
  ↓
SOS_CREATED
  ↓
QUEUED
  ↓
BROADCASTING
  ↓
RELAYED
  ↓
DELIVERED
  ↓
ACKNOWLEDGED
  ↓
RESPONDER_ASSIGNED
  ↓
RESOLVED
```
Failure branches:
```text
BROADCASTING
      ↓
NO_ROUTE
      ↓
QUEUED
      ↓
RETRY
```
or:
```text
RELAYED
   ↓
RELAY_LOST
   ↓
SEARCH_ALTERNATE_ROUTE
```
The UI must represent these states explicitly rather than showing a generic spinner.
---
11. Connection State Model
Use separate states for internet and mesh.
```text
Internet:
CONNECTED
OFFLINE

Mesh:
CONNECTED
DEGRADED
ISOLATED
SEARCHING
```
Example:
```text
Internet: OFFLINE
Mesh: CONNECTED
```
This is a valid and important emergency operating state.
---
12. Message State Model
Recommended canonical states:
```text
CREATED
QUEUED
BROADCASTING
RELAYED
DELIVERED
ACKNOWLEDGED
FAILED
EXPIRED
```
The UI should always expose the latest known state.
Avoid misleading language such as:
```text
Sent ✓
```
when the system only knows that the message was locally queued.
---
13. Topology Visualization Concept
The topology should be a simplified operational graph rather than a complex developer/network diagram.
Node Representation
Each node should communicate:
```text
NODE ID
ROLE
STATUS
```
Optional metadata:
```text
Battery
Signal
Distance
```
Link Representation
Links represent communication paths.
Possible states:
```text
HEALTHY
WEAK
ACTIVE ROUTE
FAILED
UNKNOWN
```
Active Emergency Path
When an SOS is active, visually emphasize only the path relevant to that SOS.
Example:
```text
             NODE-04
                |
                |
USER ━━━ NODE-09 ━━━ NODE-07 ━━━ RESPONDER
                  ACTIVE SOS PATH
```
The developer should implement the visualization using an efficient Compose-compatible graph/canvas approach appropriate to the project's technical constraints.
---
14. Failure-State Design Principles
Failure states are first-class UI states.
The application must handle:
No nearby nodes
No route
Relay disappeared
Message timeout
Message delivery failure
Bluetooth disabled
Required permission missing
Background service restricted
Battery critically low
Location unavailable
Mesh degraded
Device isolated
Responder unreachable
Stale node information
Every failure should answer:
What happened?
What does it affect?
What is the system doing automatically?
What can the user do?
Example:
```text
NO RELAY AVAILABLE

Your SOS is stored locally.
The system will retry automatically when another
mesh node becomes reachable.

[VIEW MESH]
```
---
15. Empty-State Design
Empty states should provide context rather than looking like broken screens.
No Nodes
```text
NO NEARBY NODES

Your device is currently isolated.

Move toward other participating devices
to improve mesh connectivity.
```
No Alerts
```text
NO ACTIVE ALERTS

No unresolved emergency alerts are currently available.
```
No Messages
```text
NO MESSAGE HISTORY

Emergency communication history will appear here.
```
No Active SOS
```text
NO ACTIVE SOS

Your device is ready for emergency communication.
```
---
16. Responder UX Principles
The responder interface should optimize for decision-making.
A responder needs to quickly answer:
Who needs help?
How severe is it?
Where are they?
When was the alert received?
Can I still reach them?
What path did the alert take?
Has another responder acknowledged it?
What should I do next?
Therefore, responder screens should prioritize:
```text
SEVERITY
↓
LOCATION
↓
RECENCY
↓
CONNECTIVITY
↓
MESSAGE
↓
RESPONSE STATUS
```
---
17. Accessibility & Usability
The application should support:
Large touch targets.
High contrast.
Android accessibility services.
Dynamic font sizing.
Screen-reader-friendly labels.
Clear focus states.
Non-color-only status indicators.
Short labels.
Minimal unnecessary animations.
Haptic feedback for critical actions where appropriate.
Confirmation for destructive actions.
Avoid:
Tiny buttons.
Dense tables.
Excessive animations.
Decorative gradients that reduce contrast.
Long paragraphs in operational screens.
Hidden emergency status.
Ambiguous icons without labels.
---
18. Animation Guidance
Animation should communicate system state, not decorate the application.
Recommended:
Subtle pulse for active SOS.
Small transition when a node becomes reachable.
Progress indicator during route discovery.
Animated topology path when an SOS is relayed.
Subtle acknowledgement transition.
Avoid:
Large page transitions.
Excessive bouncing.
Decorative particle effects.
Long loading animations.
Emergency information should never be delayed because of animation.
---
19. Jetpack Compose Implementation Guidance
The UI should be structured around reusable state-driven components.
Suggested conceptual components:
```text
EmergencyStatusBanner
SosButton
SosStatusCard
SeveritySelector
MeshStatusCard
NodeCard
NodeList
TopologyView
MessageStatusCard
SosAlertCard
ResponderIncidentCard
BatteryStatusCard
ConnectionFailureCard
EmptyState
OfflineBanner
```
Use a single source of truth for application state where appropriate.
Example conceptual UI state:
```kotlin
data class EmergencyUiState(
    val sosState: SosState,
    val meshState: MeshState,
    val nearbyNodes: List<Node>,
    val alerts: List<SosAlert>,
    val batteryPercent: Int,
    val isInternetAvailable: Boolean
)
```
The exact data models are an implementation decision and should follow the project's existing architecture.
---
20. Recommended Compose Screen Structure
Conceptual structure:
```text
App
│
├── MainScaffold
│   ├── GlobalStatus
│   ├── Navigation
│   └── ScreenContent
│
├── DashboardScreen
├── MeshScreen
├── AlertsScreen
├── ResponderScreen
│
├── SosCreationScreen
├── ActiveSosScreen
├── NodeDetailsScreen
├── TopologyScreen
├── MessageDetailsScreen
├── SosAlertDetailsScreen
└── DiagnosticsScreen
```
The lead developer can adapt this to the project's existing navigation and architecture.
---
21. Design System Summary
Visual Character
The application should feel:
Professional
Calm under pressure
Operational
Trustworthy
Fast
Technical
Mission-critical
It should not feel:
Like a college CRUD application
Like a social-media application
Like a gaming dashboard
Overly futuristic
Overly decorative
Information-heavy without hierarchy
Design Rule
> **Every pixel should help the user understand the emergency, connectivity, or next action.**
---
22. MVP Screen Priority
If development time becomes limited, implement in this order:
P0 — Essential
Emergency Dashboard
SOS Creation
Active SOS Tracking
Mesh Status
Received SOS Alerts
SOS Alert Details
P1 — Important
Nearby Nodes
Responder Dashboard
Message Status
Connection Diagnostics
P2 — Advanced
Topology Visualization
Node Details
Battery Awareness Detail Screen
Advanced network diagnostics
The MVP should never sacrifice reliable emergency-state visibility for advanced visualization.
---
23. Technical Limitation Adaptation Rule
This specification is intentionally implementation-independent.
If the lead developer identifies technical limitations, revise the design without changing the core emergency UX principles.
Examples:
If real-time topology is too expensive
Replace the live graph with:
```text
My Node
↓
Reachable Relays
↓
Estimated Route
```
If location is unavailable
Display:
```text
LOCATION UNAVAILABLE
SOS transmission continues.
```
If exact distance cannot be calculated
Use:
```text
Signal: Strong
Last seen: 3 sec ago
```
instead of inventing distance.
If background mesh communication is restricted
Make the limitation visible:
```text
BACKGROUND MESH LIMITED
Keep the application active for best reliability.
```
If responder acknowledgement cannot be guaranteed
Use:
```text
DELIVERED
Acknowledgement unavailable
```
instead of falsely showing:
```text
ACKNOWLEDGED
```
---
24. Lead Developer Handoff Checklist
Before implementation, confirm:
[ ] SOS state machine is defined.
[ ] Mesh connectivity states are defined.
[ ] Message delivery states are defined.
[ ] Severity levels are defined.
[ ] Node roles are defined.
[ ] Relay/hop information available from backend/protocol is identified.
[ ] Offline behavior is defined.
[ ] Battery constraints are defined.
[ ] Location availability behavior is defined.
[ ] Failure states are implemented.
[ ] Empty states are implemented.
[ ] Responder workflow is defined.
[ ] Compose navigation structure is agreed.
[ ] Accessibility requirements are included.
[ ] Emergency actions are protected against accidental activation.
[ ] UI does not claim delivery/acknowledgement without corresponding technical evidence.
---
25. Final UX Principle
The application is successful if a stressed user can understand the system in seconds:
```text
AM I CONNECTED?
        ↓
CAN MY SOS REACH SOMEONE?
        ↓
WHO IS RELAYING IT?
        ↓
DID A RESPONDER RECEIVE IT?
        ↓
WHAT HAPPENS NEXT?
```
The UI should make these answers immediately visible.
This design specification should be treated as the UX baseline. The lead developer may simplify or adapt individual screens based on actual mesh-protocol capabilities, Android platform limitations, performance constraints, and available backend data, while preserving emergency clarity and truthful system-state communication.