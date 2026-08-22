# About RescueMesh

## What is RescueMesh?
RescueMesh is an open-source project developed to provide a lifeline when traditional communication networks fail. It is a decentralized, offline messaging platform that relies solely on the Bluetooth hardware already present in modern smartphones.

## Why we built it
During floods, earthquakes, or other large-scale emergencies, the lack of information is often as dangerous as the disaster itself. We wanted to build a tool that allows communities to organize themselves and reach responders without needing a cellular signal. By creating a "mesh" of phones, we can extend the reach of a single device across an entire area.

## How it works (High Level)
When you open RescueMesh and start the engine, your phone begins "shouting" out its presence using Bluetooth advertisements. Other nearby phones "hear" this and establish a temporary connection. If you send an SOS, your phone hands that message to any neighbor it can find. That neighbor then carries the message until it finds another person or a responder, repeating the process until help is coordinated.

## Who it is for
- **Victims**: To broadcast location and specific needs (medical, fire, food).
- **Responders**: To receive and aggregate reports into a real-time incident map.
- **Relay Nodes**: Anyone with a smartphone can help extend the network by simply keeping the app active.

## Core Technologies
- **BLE Mesh**: Custom-built transport layer optimized for Android devices.
- **Cryptography**: Using the phone's secure hardware to sign messages, ensuring help requests are genuine.
- **Offline Maps**: Using device sensors and GPS to provide location context without a data connection.

## Practical Use Cases
- **Mountain Rescue**: Coordinating in areas with zero reception.
- **Natural Disasters**: Maintaining a community network after infrastructure collapse.
- **Mass Gatherings**: Providing an emergency channel during network congestion at large events.
