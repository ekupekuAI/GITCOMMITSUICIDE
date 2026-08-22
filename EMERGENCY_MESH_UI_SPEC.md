# INFRASTRUCTURE-INDEPENDENT EMERGENCY COMMUNICATION MESH
# MASTER UI/UX + JETPACK COMPOSE IMPLEMENTATION SPECIFICATION

## ROLE

You are the lead Android UI engineer and UI/UX implementation specialist.

Build a professional Android emergency-response interface for:

"Infrastructure-Independent Emergency Communication Mesh"

The application must communicate one core idea:

A device should be able to create and exchange emergency messages through nearby devices and relay nodes even when normal internet infrastructure is unavailable.

The UI must look like a professional emergency-response / disaster-response / communication operations system.

It must NOT look like:

- A college CRUD project
- A generic admin dashboard
- A banking application
- A social media application
- A simple form application
- A gaming interface
- A colorful cartoon application
- A generic Material demo

The interface must prioritize:

1. Emergency clarity
2. Speed
3. Reliability
4. Network visibility
5. Responder usability
6. Accessibility
7. Technical credibility
8. Simple understanding
9. Professional visual hierarchy

DO NOT build the actual mesh networking protocol unless explicitly requested.

Use clean mock/demo data where real backend/network services are not available.

Structure the code so real networking services can later replace mock implementations.

---

# 1. CORE PRODUCT CONCEPT

The application connects emergency users and responders through a local mesh.

Concept:

USER
  ↓
LOCAL NODE DISCOVERY
  ↓
RELAY NODE
  ↓
RELAY NODE
  ↓
RESPONDER
  ↓
EMERGENCY DELIVERED

The application should visually demonstrate:

- Node discovery
- Network availability
- Relay discovery
- Multi-hop communication
- Message queueing
- Message delivery
- Connection failures
- Route recovery
- Emergency severity
- Responder coordination

---

# 2. PRIMARY USER TYPES

Support two major user experiences.

## A. Emergency User

The user needs to:

- See current network status
- See their node status
- Create SOS
- Select severity
- Send emergency messages
- See SOS delivery progress
- See relay hops
- See nearby nodes
- Understand offline mode
- Understand failures
- See responder acknowledgement
- Monitor battery

## B. Responder

The responder needs to:

- See active emergencies
- Prioritize critical incidents
- View incident location
- View relay path
- View network topology
- Acknowledge incidents
- Mark incidents as responding
- Send response messages
- Monitor communication status
- See affected nodes
- Understand network health

---

# 3. MAIN NAVIGATION

Use bottom navigation with four primary destinations:

1. Dashboard
2. Network
3. Messages
4. Responder

Suggested structure:

APP
│
├── DASHBOARD
│   ├── SOS Creation
│   ├── SOS Status
│   ├── Emergency Summary
│   └── Device Status
│
├── NETWORK
│   ├── Network Overview
│   ├── Nearby Nodes
│   ├── Topology
│   ├── Routes
│   └── Diagnostics
│
├── MESSAGES
│   ├── All Messages
│   ├── SOS Messages
│   ├── Incoming
│   ├── Outgoing
│   └── Message Details
│
└── RESPONDER
    ├── Active Incidents
    ├── Incident Details
    ├── Responder Actions
    └── Response Messages

Settings can be accessible from the top app bar.

SOS must remain highly visible from Dashboard.

---

# 4. DASHBOARD

## Purpose

The dashboard is the emergency command center.

The user should understand the current situation within seconds.

## Required information

Display:

- Mesh status
- Node ID
- Node role
- Battery
- Nearby nodes
- Relay availability
- Network reachability
- Active emergency count
- Latest message status
- Current SOS status
- Last synchronization
- Offline status

## Layout

Top:

EMERGENCY MESH

● MESH OPERATIONAL

Device:
N-104

Battery:
78%

Nearby Nodes:
6

Relay Paths:
3

Large central action:

[ SEND SOS ]

Then:

NETWORK STATUS

● Mesh operational
● Bluetooth available
● Wi-Fi Direct available
○ Internet unavailable

Then:

ACTIVE EMERGENCIES

🔴 1 Critical
🟠 2 High
🟡 3 Moderate

Then:

LATEST ACTIVITY

SOS #A104
Relaying • 3 hops

## Dashboard quick actions

Provide quick access to:

- Send SOS
- Nearby Nodes
- Network Topology
- Messages
- Active Alerts

---

# 5. SOS CREATION

The SOS workflow must be extremely simple.

## Required fields

- Severity
- Emergency type
- Optional description
- Location
- Location accuracy
- Node ID

## Emergency types

Include useful categories:

- Medical
- Fire
- Trapped Person
- Accident
- Natural Disaster
- Security Threat
- Missing Person
- Infrastructure Failure
- Other

Do not overwhelm the user.

Use a clear grid/list of emergency types.

## Severity

CRITICAL
HIGH
MODERATE

Critical must be visually dominant.

## Location

Show:

LOCATION

● GPS AVAILABLE

Accuracy:
± 12 m

If unavailable:

⚠ LOCATION UNAVAILABLE

Allow the user to continue if the system supports manual/approximate location.

## SOS confirmation

Before final transmission:

CRITICAL SOS

Emergency:
Medical

Location:
Available

Network:
3 relay paths

[ SEND SOS ]

---

# 6. SOS TRANSMISSION STATUS

Use a visual timeline.

CREATED
 ↓
QUEUED
 ↓
BROADCAST
 ↓
RELAYING
 ↓
RESPONDER FOUND
 ↓
DELIVERED

Show:

- SOS ID
- Current status
- Number of hops
- Relay nodes
- Transmission time
- Last successful relay
- Current route
- Retry/recovery state

Example:

SOS #A104

🔴 CRITICAL

✓ Created
✓ Broadcast
✓ N207 relay
✓ N312 relay
◉ Responder reached

3 HOPS

12.4 SEC

✓ DELIVERED

---

# 7. MESSAGE STATUS SYSTEM

Messages must have clear states.

## States

QUEUED

Message stored locally and waiting for a relay.

BROADCASTING

Message is being transmitted to nearby nodes.

RELAYING

Message is travelling through one or more relay nodes.

DELIVERED

Message reached its destination.

ACKNOWLEDGED

Responder received and acknowledged the message.

INTERRUPTED

Transmission stopped because the current route failed.

EXPIRED

Message could not be delivered within the configured lifetime.

## Visual representation

Use:

Icon + text + supporting information.

Do not depend on color alone.

---

# 8. MESSAGES SCREEN

The Messages screen is mandatory.

Example:

MESSAGES

FILTER:
All | SOS | Incoming | Outgoing

🔴 SOS #A104
Delivered • 12 sec
3 hops

🟠 Assistance #A102
Relaying • 2 hops

⚠ Status #A099
Delivery interrupted

Each card should show:

- Severity
- Message type
- ID
- Status
- Time
- Hop count
- Direction
- Delivery state

Support search/filtering if technically reasonable.

---

# 9. MESSAGE DETAILS

Show:

MESSAGE #A104

Type:
Emergency SOS

Severity:
CRITICAL

Origin:
N-104

Destination:
Responder Network

Created:
14:32:05

Delivered:
14:32:17

Total hops:
3

ROUTE

N-104
 ↓
N-207
 ↓
N-312
 ↓
N-415
 ↓
RESPONDER

STATUS

✓ DELIVERED

---

# 10. MESH NETWORK SCREEN

The Network screen should act as the network operations center.

Show:

MESH STATUS

● OPERATIONAL

Nodes:
12

Active Links:
18

Relay Nodes:
7

Reachable:
GOOD

Connectivity:

Bluetooth     ● Active
Wi-Fi Direct  ● Active
Internet      ○ Unavailable

Available Routes:
3

Network Stability:
GOOD

---

# 11. NETWORK HEALTH SCORE

Provide a simple human-readable network health indicator.

Example:

NETWORK HEALTH

████████░░ 82%

GOOD

Avoid making this look like fake scientific accuracy.

Use descriptive categories:

EXCELLENT
GOOD
DEGRADED
CRITICAL
ISOLATED

If a numeric score is used, clearly treat it as a UI summary rather than a precise real-world metric unless the backend provides one.

---

# 12. NEARBY NODES

Display nearby devices.

Example:

N-207
RELAY
8m
Signal: Strong
Battery: 84%
Last seen: 3 sec

N-312
RESPONDER
17m
Signal: Medium
Battery: 63%
Last seen: 5 sec

N-415
DEVICE
25m
Signal: Weak
Battery: 31%
Last seen: 8 sec

Node roles:

USER
RELAY
RESPONDER
DEVICE
UNKNOWN

---

# 13. NODE DETAILS

When tapping a node:

NODE N-207

Role:
RELAY

Battery:
84%

Signal:
Strong

Distance:
8m

Last Seen:
3 sec ago

Active Routes:
2

Messages Relayed:
17

Connection:
ACTIVE

Actions:

[ VIEW ROUTE ]

[ VIEW TOPOLOGY ]

---

# 14. MESH TOPOLOGY

This is a mandatory showcase feature.

Do NOT replace it with a list.

Use a graphical network visualization.

Concept:

                  N312
                   ●
                  / \
                 /   \
        N207 ●         ● N415
             \         /
              \       /
               ●─────●
             USER   RESPONDER

The actual UI must use graphical nodes and connection lines.

## Node appearance

USER:
Large highlighted node

RELAY:
Normal node with relay indicator

RESPONDER:
Distinct responder icon

UNKNOWN:
Neutral node

## Connection lines

Normal:
Standard connection

Weak:
Dashed/thinner line

Active:
Highlighted connection

Failed:
Broken/dashed connection

## Active SOS route

USER → N207 → N312 → RESPONDER

The active route must be visually emphasized.

---

# 15. TOPOLOGY INTERACTION

Support:

- Zoom
- Pan
- Node selection
- Route highlighting
- Node details
- Active route
- Failed route
- Relay path
- Hop count

If full zoom/pan is technically too complex initially, implement a clean scalable topology layout first and preserve the architecture for future interaction.

---

# 16. LIVE ROUTE VISUALIZATION

When SOS is active:

USER
 ↓
N207
 ↓
N312
 ↓
RESPONDER

Show subtle packet movement.

The animation must communicate:

"Message is travelling through the mesh."

Do not use decorative animations unrelated to networking.

---

# 17. ROUTE INFORMATION

Show:

ACTIVE ROUTE

N104 → N207 → N312 → N415

Hops:
3

Route status:
ACTIVE

Estimated transmission:
12 sec

Last relay:
N312

If route fails:

ROUTE INTERRUPTED

Last successful relay:
N312

Searching for alternative route...

---

# 18. ROUTE RECOVERY

Support a visible recovery state.

Example:

CONNECTION LOST

N312 unavailable

Searching for alternate relay...

Then:

ALTERNATE ROUTE FOUND

N104
 ↓
N207
 ↓
N415
 ↓
RESPONDER

Transmission resumed.

This is a major demonstration feature.

---

# 19. OFFLINE MODE

Offline mode is a first-class application state.

Show:

OFFLINE

Internet unavailable.

Mesh:
● Operational

or:

OFFLINE

Internet unavailable.

Mesh:
▲ Degraded

or:

OFFLINE

No relay path available.

SOS messages should remain queued if possible.

Example:

SOS QUEUED

No relay currently available.

Your emergency message is stored locally
and will be transmitted when a relay becomes available.

[ VIEW QUEUE ]

---

# 20. LOCAL MESSAGE QUEUE

Provide a queue indicator.

Example:

QUEUED MESSAGES
3

Messages waiting for relay transmission.

Show:

- Message ID
- Severity
- Age
- Retry state
- Current queue position

Example:

🔴 SOS #A104
Waiting for relay
12 sec

🟠 Assistance #A103
Waiting for relay
24 sec

---

# 21. CONNECTION DIAGNOSTICS

Show understandable diagnostics.

Example:

CONNECTION DIAGNOSTICS

Bluetooth
✓ Available

Wi-Fi Direct
✓ Available

Nearby Nodes
0

Relay Paths
0

Internet
✕ Unavailable

Mesh State
ISOLATED

Recommendation:

Move closer to another mesh node
to establish communication.

---

# 22. BATTERY AWARENESS

Always display battery state where useful.

Normal:

🔋 78%

Low:

🔋 21%
LOW BATTERY

Critical:

🔋 8%
CRITICAL BATTERY

Battery information should appear in:

- Dashboard
- Node details
- Nearby node cards
- Topology node details
- Responder information when relevant

Avoid overwhelming the primary UI.

---

# 23. POWER-SAVING MODE

If appropriate for the project architecture, provide:

POWER SAVING

Reduce scanning frequency
to preserve battery.

Options:

NORMAL
BALANCED
POWER SAVING

This should be clearly explained.

Do not implement real power management unless the backend/device layer supports it.

---

# 24. RESPONDER MODE

Responder Mode is mandatory.

Layout:

RESPONDER MODE

🔴 3 CRITICAL
🟠 5 HIGH
🟡 7 MODERATE

ACTIVE INCIDENTS

🔴 A104
3 hops • 8 sec

🔴 A102
4 hops • 12 sec

🟠 A098
2 hops • 35 sec

[ NETWORK TOPOLOGY ]

## Responder features

- Incident list
- Severity sorting
- Latest incidents
- Acknowledge
- Responding state
- Location
- Relay path
- Network topology
- Response messaging
- Incident status

---

# 25. INCIDENT PRIORITIZATION

Sort incidents using:

1. Severity
2. Recency
3. Delivery status
4. Distance if available
5. Responder assignment

Critical emergencies should appear first.

---

# 26. INCIDENT DETAIL

Example:

CRITICAL INCIDENT

SOS #A104

Medical Emergency

LOCATION

● Available

Origin:
N104

Distance:
1.2 km

RELAY PATH

N104
 ↓
N207
 ↓
N312
 ↓
RESPONDER

STATUS

✓ DELIVERED

ACTIONS:

[ ACKNOWLEDGE ]

[ RESPONDING ]

[ SEND MESSAGE ]

[ VIEW TOPOLOGY ]

---

# 27. RESPONDER ACKNOWLEDGEMENT

After acknowledgement:

INCIDENT ACKNOWLEDGED

Responder:
R-001

Time:
14:35:12

Status:

ACKNOWLEDGED

Then:

RESPONDING

Then:

RESOLVED

Incident lifecycle:

RECEIVED
 ↓
ACKNOWLEDGED
 ↓
RESPONDING
 ↓
RESOLVED

---

# 28. RESPONSE MESSAGES

Responder should be able to send:

- Help is on the way
- Stay where you are
- Move to safe location
- Provide additional information
- Unable to reach location
- Custom message

Example:

RESPONDER MESSAGE

[ Help is on the way ]

[ Stay where you are ]

[ Move to safe location ]

[ CUSTOM MESSAGE ]

Messages should travel through the same mesh communication mechanism conceptually.

---

# 29. RECEIVED ALERTS

Users should be able to see relevant emergency alerts.

Example:

EMERGENCY ALERT

🔴 CRITICAL

SOS #A104

Medical Emergency

3 hops

Delivered 12 sec ago

Location available

[ VIEW INCIDENT ]

---

# 30. LOCATION STATUS

Location should have clear states.

Available:

● LOCATION AVAILABLE

Unavailable:

⚠ LOCATION UNAVAILABLE

Searching:

◉ ACQUIRING LOCATION

Low accuracy:

⚠ LOW LOCATION ACCURACY

Do not show fake GPS data in production.

For demo mode, clearly label simulated locations.

---

# 31. MAP / LOCATION VIEW

If location functionality is implemented, provide a simple incident location view.

Show:

- User location
- Incident location
- Responder location when available
- Approximate distance
- Location accuracy

Do not make the map the primary screen.

Emergency information must remain more important.

---

# 32. EMERGENCY NOTIFICATION BANNER

When a critical emergency arrives, show a prominent alert banner.

Example:

🔴 CRITICAL EMERGENCY

SOS #A104

Immediate response required.

[ VIEW ]

Do not allow the notification to permanently cover the application.

---

# 33. NETWORK EVENT LOG

Provide an optional technical event log.

Example:

14:32:05
SOS created

14:32:06
Broadcast started

14:32:07
Relay N207 discovered

14:32:09
Relay N312 discovered

14:32:12
Responder reached

14:32:17
SOS delivered

This is especially useful for:

- Developers
- Judges
- Demonstrations
- Debugging

Keep it secondary to normal emergency UI.

---

# 34. SYSTEM EVENT LOG

Possible events:

NODE_DISCOVERED
NODE_LOST
RELAY_FOUND
RELAY_LOST
MESSAGE_QUEUED
MESSAGE_BROADCAST
MESSAGE_RELAYED
MESSAGE_DELIVERED
MESSAGE_INTERRUPTED
ROUTE_FOUND
ROUTE_LOST
ROUTE_RECOVERED
BATTERY_LOW
MESH_DEGRADED
MESH_RECOVERED

Human-readable UI text should be shown instead of raw event names.

---

# 35. NETWORK RECOVERY INDICATOR

When network recovers:

MESH RECOVERED

✓ Relay path available

3 routes discovered

Queued messages:
2

[ VIEW QUEUE ]

This makes recovery visible to the user.

---

# 36. SEARCH / FILTER

Where technically useful, support:

Message search

Incident filtering:

All
Critical
High
Moderate
Acknowledged
Unresolved
Delivered
Interrupted

Node filtering:

All
Relay
Responder
Device

Do not add filters where they make the interface unnecessarily complicated.

---

# 37. SORTING

Responder incidents:

Critical first
Newest first

Messages:

Newest first

Nodes:

Strongest signal first

Topology:

No sorting required

---

# 38. SETTINGS

Provide a simple settings screen.

Possible sections:

DEVICE

Node ID
Node Role
Battery

NETWORK

Bluetooth
Wi-Fi Direct
Discovery
Scanning frequency

EMERGENCY

Default severity
Location behavior
SOS confirmation

NOTIFICATIONS

Emergency alerts
Responder notifications
Delivery notifications

DEVELOPER / DEMO

Demo mode
Mock data
Network simulation

Do not expose developer controls in the normal user experience unless useful for the hackathon.

---

# 39. DEMO MODE

Create a demo mode if the real backend is unavailable.

Demo mode should allow judges to see:

1. Normal network
2. Nearby nodes
3. Topology
4. SOS creation
5. Relay path
6. Responder reception
7. Delivery
8. Failure
9. Recovery
10. Offline queue

Example demo scenario:

USER:
N104

RELAYS:
N207
N312

RESPONDER:
R001

SOS:
A104

Flow:

N104
 ↓
N207
 ↓
N312
 ↓
R001

Then:

DELIVERED

3 HOPS

12 SEC

---

# 40. FAILURE SIMULATION

If demo mode is available, allow:

[ SIMULATE CONNECTION FAILURE ]

Then show:

⚠ ROUTE INTERRUPTED

N312 disconnected.

Searching for alternate relay...

Then:

ALTERNATE ROUTE FOUND

N104
 ↓
N207
 ↓
N415
 ↓
R001

✓ TRANSMISSION RESUMED

This is highly valuable for the hackathon demonstration.

---

# 41. EMPTY STATES

Every major list needs an appropriate empty state.

No emergencies:

✓ NO ACTIVE EMERGENCIES

No nearby nodes:

NO NEARBY NODES

Scanning for nearby devices...

No messages:

NO MESSAGES

Messages sent through the mesh
will appear here.

No routes:

NO RELAY PATH AVAILABLE

No queued messages:

✓ QUEUE EMPTY

Do not use generic:

"No data found."

---

# 42. LOADING STATES

Use meaningful loading messages.

Node discovery:

SCANNING FOR NODES...

Network:

ANALYZING MESH...

Location:

ACQUIRING LOCATION...

Route:

FINDING RELAY PATH...

SOS:

BROADCASTING SOS...

Do not display a generic spinner without context.

---

# 43. ERROR STATES

Errors must explain:

What happened
What it means
What the system is doing
What the user can do

Example:

RELAY CONNECTION LOST

The current route is unavailable.

Searching for another relay path...

Last successful relay:
N312

---

# 44. COLOR SYSTEM

Use restrained professional colors.

Critical:
Red

High:
Orange

Moderate:
Amber/Yellow

Operational:
Green

Information:
Blue

Offline:
Neutral Gray

Background:
Dark Neutral

Primary Text:
White

Secondary Text:
Light Gray

Use colors consistently.

Never use color as the only state indicator.

---

# 45. TYPOGRAPHY

Use Roboto / Material 3 typography.

Suggested:

Emergency display:
32–40sp

Screen title:
24sp

Section heading:
18–20sp

Primary information:
16sp

Secondary:
14sp

Metadata:
12sp

Emergency numbers can be larger.

---

# 46. MATERIAL 3

Use Material 3 where appropriate.

Use:

- Cards
- Buttons
- Navigation bar
- Dialogs
- Chips
- Lists
- Icons
- Top app bars

But do not make the UI look like a generic Material template.

Customize:

- Spacing
- Colors
- Emergency components
- Network visualization
- Status cards
- SOS interaction

---

# 47. SOS BUTTON

The SOS button must be visually dominant.

Example:

[ SEND SOS ]

Requirements:

- Large touch target
- Clear text
- High contrast
- Easy to locate
- Easy to understand
- Not hidden in menus

Do not make the SOS button a tiny icon.

---

# 48. ACCESSIBILITY

The application must support emergency use under stress.

Requirements:

- High contrast
- Large touch targets
- Clear labels
- Icons + text
- Color + text
- Readable typography
- No tiny status indicators
- Avoid information overload
- Consistent component placement

The interface must remain understandable even if the user does not understand networking technology.

---

# 49. THREE LEVEL INFORMATION MODEL

Every important state should have three levels.

LEVEL 1 — SIMPLE

🔴 SOS DELIVERED

LEVEL 2 — OPERATIONAL

Delivered through 3 relay hops.

LEVEL 3 — TECHNICAL

N104 → N207 → N312 → N415

RSSI
Battery
Timestamp
Route metrics

Normal users see Level 1.

Responders can access Level 2.

Technical details can be accessed through deeper screens.

---

# 50. EMERGENCY ALERT HIERARCHY

Critical:

🔴 CRITICAL

High:

🟠 HIGH

Moderate:

🟡 MODERATE

Information:

🔵 INFORMATION

Never visually compete with Critical emergencies.

---

# 51. ANIMATION

Use animation only when it improves understanding.

Useful animations:

- Node discovery
- Active relay
- Packet movement
- Route recovery
- Status transition
- Network recovery

Avoid:

- Excessive bouncing
- Large transitions
- Decorative particles
- Gaming effects
- Long animations

Emergency actions must be immediate.

---

# 52. COMPONENT ARCHITECTURE

Create reusable Compose components.

Required components:

EmergencyStatusCard
EmergencySeverityChip
SOSButton
SOSConfirmationCard
SOSStatusTimeline
NodeStatusCard
NearbyNodeCard
NetworkStatusCard
NetworkHealthCard
TopologyView
TopologyNode
TopologyConnection
ActiveRoute
RelayPath
MessageCard
MessageStatusIndicator
MessageTimeline
IncidentCard
IncidentPriorityCard
ResponderDashboard
ResponderIncidentCard
BatteryIndicator
SignalIndicator
ConnectionIndicator
OfflineBanner
FailureState
EmptyState
LoadingState
RecoveryState
EventLog
LocationStatusCard
ResponseActionBar

Avoid duplicated UI code.

---

# 53. DATA MODEL CONCEPT

Node:

nodeId
role
batteryLevel
signalStrength
distance
lastSeen
isRelay
isResponder
connectionState

SOS:

sosId
severity
type
description
originNode
location
locationAccuracy
status
createdAt
deliveredAt
hopCount
relayPath

MeshStatus:

overallStatus
nearbyNodeCount
activeLinkCount
relayCount
availableRoutes
bluetoothStatus
wifiDirectStatus
internetStatus
lastSync

Message:

messageId
type
severity
status
origin
destination
hopCount
relayPath
timestamp

Incident:

incidentId
severity
type
location
originNode
status
createdAt
acknowledgedAt
responderId
relayPath

---

# 54. UI STATE MODEL

Every major screen should account for:

LOADING
NORMAL
EMPTY
OFFLINE
DEGRADED
CRITICAL
ERROR
RECOVERING

Do not design only the happy path.

---

# 55. ARCHITECTURE

Use clean separation:

Mesh / Network Layer
        ↓
Repository
        ↓
ViewModel
        ↓
UI State
        ↓
Jetpack Compose

Compose must not directly manage networking.

Do not put networking code inside Composables.

---

# 56. PROJECT STRUCTURE

Use a clean structure similar to:

ui/
    navigation/
    theme/
    components/
    dashboard/
    sos/
    network/
    messages/
    responder/
    settings/

data/
    models/
    repository/
    datasource/

domain/
    usecase/

Do not over-engineer.

Adapt this structure to the existing project.

---

# 57. MOCK DATA

If the actual mesh backend is not implemented, create a clean demo/mock data layer.

Use realistic example nodes:

N104
N207
N312
N415
N512

Responder:

R001

Example SOS:

A104
A102
A098

Make mock data clearly replaceable.

Do not mix mock data directly throughout Composables.

---

# 58. REQUIRED DEMO WORKFLOW

The application must demonstrate:

STEP 1

Dashboard

MESH OPERATIONAL

STEP 2

Nearby Nodes

N207
N312
N415

STEP 3

Topology

USER
 ↓
N207
 ↓
N312
 ↓
RESPONDER

STEP 4

Create SOS

🔴 CRITICAL

STEP 5

SOS STATUS

CREATED
 ↓
BROADCAST
 ↓
RELAYING
 ↓
DELIVERED

STEP 6

Messages

🔴 SOS #A104
Delivered
3 hops

STEP 7

Responder

🔴 A104
3 hops
Delivered

STEP 8

Incident Detail

[ ACKNOWLEDGE ]
[ RESPONDING ]

STEP 9

Simulate failure

ROUTE INTERRUPTED

STEP 10

Recovery

ALTERNATE ROUTE FOUND

STEP 11

Delivery

✓ SOS DELIVERED

This workflow must feel like one coherent product.

---

# 59. HACKATHON PRESENTATION REQUIREMENT

The UI should visually communicate the project's technical innovation.

A judge should be able to understand within seconds:

"This application does not depend entirely on the internet."

The UI should demonstrate:

Internet:
✕ UNAVAILABLE

Mesh:
● OPERATIONAL

Nearby Nodes:
6

Relay Paths:
3

SOS:
✓ DELIVERED

This is a very important visual message.

---

# 60. NETWORK INDEPENDENCE VISUAL

When internet is unavailable but mesh works:

INTERNET
✕

MESH
✓ OPERATIONAL

SOS
✓ DELIVERED

This should be clearly visible.

This is one of the project's strongest differentiators.

---

# 61. PROFESSIONAL DESIGN LANGUAGE

The final application should feel:

Professional
Serious
Reliable
Fast
Technical
Calm
High-contrast
Emergency-focused

Avoid making every card highly rounded or brightly colored.

Use visual hierarchy instead of decoration.

---

# 62. RESPONSIVE DESIGN

The application should work across different Android screen sizes.

Avoid hard-coded dimensions wherever possible.

Use:

- dp
- sp
- responsive layouts
- LazyColumn
- adaptive containers
- proper Compose constraints

The topology should adapt to different screen sizes.

---

# 63. PERFORMANCE

Do not create unnecessary recompositions.

Avoid expensive animations.

Use LazyColumn/LazyRow for lists.

Keep topology rendering efficient.

Do not repeatedly recreate static objects unnecessarily.

---

# 64. ERROR HANDLING

The application must never crash because demo/mock data is missing.

Provide sensible fallback UI.

Example:

Unable to load network status.

[ RETRY ]

But for emergency communication failures, use meaningful language:

NO RELAY PATH AVAILABLE

rather than:

NullPointerException
Network error
HTTP 500

---

# 65. SECURITY-STYLE VISUAL LANGUAGE

The application may use subtle operational terminology:

NODE
RELAY
ROUTE
HOP
MESH
INCIDENT
TRANSMISSION
DELIVERY
ACKNOWLEDGED
RESPONDING

Do not overload normal users with technical terms.

Expose technical details progressively.

---

# 66. FINAL SCREEN INVENTORY

The implementation should include at least:

1. Dashboard
2. SOS Creation
3. SOS Confirmation
4. SOS Transmission Status
5. Network Status
6. Nearby Nodes
7. Node Details
8. Mesh Topology
9. Route Details
10. Messages
11. Message Details
12. Received SOS Alerts
13. Responder Dashboard
14. Incident Details
15. Response Message
16. Connection Diagnostics
17. Offline Mode
18. Queued Messages
19. Event Log
20. Settings

Not every screen must appear in bottom navigation.

Use navigation appropriately.

---

# 67. MANDATORY FEATURES CHECKLIST

Before declaring the implementation complete, verify:

[ ] Dashboard
[ ] Large SOS action
[ ] SOS creation
[ ] Emergency severity
[ ] Emergency type
[ ] Location state
[ ] SOS confirmation
[ ] SOS transmission status
[ ] Queued state
[ ] Broadcast state
[ ] Relaying state
[ ] Delivered state
[ ] Interrupted state
[ ] Relay path
[ ] Hop count
[ ] Network status
[ ] Mesh health
[ ] Nearby nodes
[ ] Node details
[ ] Graphical topology
[ ] Active route visualization
[ ] Messages screen
[ ] Message details
[ ] Received SOS alerts
[ ] Responder Mode
[ ] Active incident list
[ ] Incident details
[ ] Acknowledge action
[ ] Responding state
[ ] Response message
[ ] Connection diagnostics
[ ] Offline mode
[ ] Local queue UI
[ ] Battery awareness
[ ] Location status
[ ] Failure states
[ ] Recovery states
[ ] Empty states
[ ] Loading states
[ ] Event log
[ ] Demo/mock mode
[ ] Failure simulation
[ ] Network recovery simulation
[ ] Accessibility
[ ] Responsive layout

---

# 68. CRITICAL REQUIREMENT

DO NOT OMIT THESE THREE MAJOR FEATURES:

## RESPONDER MODE

RESPONDER MODE

🔴 3 CRITICAL
🟠 5 HIGH
🟡 7 MODERATE

ACTIVE INCIDENTS

🔴 A104
3 hops • 8 sec

🔴 A102
4 hops • 12 sec

🟠 A098
2 hops • 35 sec

[ NETWORK TOPOLOGY ]

---

## ACTIVE MESH TOPOLOGY

USER
 ↓
N207
 ↓
N312
 ↓
RESPONDER

Show this graphically.

---

## MESSAGES

🔴 SOS #A104
Delivered • 12 sec
3 hops

🟠 Assistance #A102
Relaying • 2 hops

⚠ Status #A099
Delivery interrupted

These are mandatory core features.

---

# 69. FINAL QUALITY BAR

The final UI should answer these questions immediately:

1. Can I send an SOS?
2. Is my mesh working?
3. Is internet unavailable?
4. Can my device still communicate?
5. Are there nearby nodes?
6. Which nodes can relay my message?
7. Where is my SOS currently?
8. How many hops has it travelled?
9. Did the responder receive it?
10. If the route failed, what happened?
11. Is another route available?
12. What emergencies are active?
13. Which emergency is most critical?
14. Can the responder acknowledge it?
15. Can the responder send a response?
16. How healthy is my battery?
17. What is the current network topology?

If the user can answer these questions easily, the UI is successful.

---

# 70. FINAL PRODUCT STORY

The entire application should visually communicate:

                    EMERGENCY
                         ↓
                   CREATE SOS
                         ↓
                  NODE DISCOVERY
                         ↓
                  RELAY DISCOVERY
                         ↓
                    N207 RELAY
                         ↓
                    N312 RELAY
                         ↓
                     RESPONDER
                         ↓
                    DELIVERED
                         ↓
                  ACKNOWLEDGED
                         ↓
                    RESPONDING

Failure path:

                   CONNECTION LOST
                         ↓
                    SOS QUEUED
                         ↓
                SEARCHING FOR RELAY
                         ↓
                 ALTERNATE ROUTE
                         ↓
                  TRANSMISSION
                         ↓
                     DELIVERED

The UI must make this story visually understandable without requiring the judge to read technical documentation.

---

# 71. CODEX IMPLEMENTATION INSTRUCTION

Read this entire specification before modifying the project.

First:

1. Inspect the existing project.
2. Identify whether it is an Android/Jetpack Compose project.
3. Inspect existing Gradle configuration.
4. Inspect existing source files.
5. Preserve working code.
6. Do not unnecessarily recreate the project.
7. Identify missing dependencies.
8. Implement the UI incrementally.
9. Keep mock/demo data separate from UI.
10. Use reusable Compose components.
11. Implement navigation.
12. Implement all mandatory screens.
13. Implement all major states.
14. Implement topology visualization.
15. Implement SOS workflow.
16. Implement messages.
17. Implement responder workflow.
18. Implement failure/recovery states.
19. Run/build the project.
20. Fix compilation errors.
21. Fix runtime/navigation issues.
22. Verify every mandatory feature checklist item.

Do not stop after creating the Dashboard.

Do not omit:

- Responder Mode
- Messages
- Message Details
- Mesh Topology
- Active Route
- Relay/Hop information
- Connection Failure
- Offline Queue
- Recovery
- Incident Details

The UI must be professional enough for a hackathon final demonstration.

Do not implement fake real networking.

Use demo/mock state only where necessary and keep it clearly separated so real mesh services can later replace it.

When technical limitations prevent a feature from being implemented exactly as specified, preserve the UX intent and implement the closest technically appropriate version without breaking the application.

After implementation, verify that the complete emergency workflow works from:

Dashboard
→ SOS
→ Transmission
→ Relay
→ Topology
→ Messages
→ Responder
→ Incident
→ Acknowledgement
→ Failure
→ Recovery
→ Delivery