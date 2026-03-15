# Decentralized Land Registration System (DLRS)

![Hyperledger Fabric](https://img.shields.io/badge/Hyperledger-Fabric%202.5-blue)
![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Backend-SpringBoot-green)
![React](https://img.shields.io/badge/Frontend-React-blue)
![License](https://img.shields.io/badge/License-Apache%202.0-lightgrey)
![Blockchain](https://img.shields.io/badge/Architecture-Blockchain-purple)

## Overview

The **Decentralized Land Registration System (DLRS)** is an enterprise-grade blockchain platform designed to migrate national land records to a **permissioned distributed ledger**.

Traditional land registry systems suffer from:

- Record tampering
- Lack of transparent provenance
- Double-spending of real estate assets
- Centralized points of failure

DLRS solves these problems by anchoring a **Unique Land Parcel Identification Number (ULPIN)** to physical **GPS coordinates** and storing ownership records on **Hyperledger Fabric**.

This ensures:

- Cryptographic proof of ownership
- Immutable transaction history
- Transparent lineage tracking for land mutations

---

# Project Information

| Attribute | Value |
|--------|--------|
| Repository | Land-Registration-Using-Blockchain |
| Author | PR Project |
| GitHub | BitForge95 |
| Year | 2026 |
| License | Apache 2.0 |

---

# Architecture

The system follows a **full-stack decentralized application architecture**.

```
User (Citizen / Admin)
        |
        v
React Frontend
        |
        v
Spring Boot REST API
        |
        v
Hyperledger Fabric Gateway SDK
        |
        v
Hyperledger Fabric Network
        |
        v
Peer Nodes + CouchDB State Database
        |
        v
IPFS (Off-chain document storage)
```

---

# Technology Stack

### Blockchain Layer
- **Hyperledger Fabric v2.5**
- **Consensus:** Raft (Crash Fault Tolerant)
- **Smart Contracts:** Java Chaincode

### Backend
- **Spring Boot (Java 17)**
- **REST API**
- **Fabric Gateway SDK**

### Frontend
- **React.js**

### Databases
- **CouchDB** (for rich JSON queries)

### Off-chain Storage
- **IPFS** (document storage using CID)

---

# Blockchain Network Topology

The Fabric network simulates a **government-grade deployment model**.

| Component | Configuration |
|----------|---------------|
| Organizations | 2 |
| Org1 | Ministry of Rural Development |
| Org2 | State Revenue Department |
| Peers | 1 Peer per Organization |
| Orderers | 3 Raft Orderer Nodes |
| Channel | `landchannel` |
| State Database | CouchDB |

This configuration ensures **crash fault tolerance and distributed governance**.

---

# Repository Structure

```
Land-Registration-Using-Blockchain
│
├── chaincode
│   └── Smart contract implementation (Java)
│
├── blockchain-network
│   └── Fabric network configuration
│   └── Docker compose files
│
├── backend-api
│   └── Spring Boot REST API
│
├── frontend-ui
│   └── React application
│
└── docs
    └── Architecture diagrams
```

---

# Smart Contract (Chaincode)

The **LandRegistryContract** implements the core business logic of the land registry system.

### Implemented Functions

| Function | Description |
|--------|--------|
| `initLedger` | Initializes the ledger with genesis data |
| `createLandAsset` | Registers a new land asset |
| `transferLandOwnership` | Transfers ownership to another entity |

### Upcoming Functions

| Function | Description |
|--------|--------|
| `mutateLand` | Handles land splits or merges |
| `queryLandByUlpin` | Fetches the current land state |
| `getLandHistory` | Retrieves immutable ownership history |

---

# Land Asset Data Model

Each land parcel is represented by a **LandAsset object** stored on the ledger.

| Field | Description |
|------|-------------|
| `ulpin` | Unique Land Parcel Identification Number |
| `ownerAadhaarHash` | Privacy-preserving owner identifier |
| `gpsCoordinates` | Geographic anchor for the land |
| `documentCid` | IPFS content identifier for land documents |
| `status` | ACTIVE / RETIRED_MUTATED |
| `parentUlpin` | Parent parcel identifier for lineage tracking |

---

# Key Features

### Implemented Features

- Land registration with duplicate prevention
- Secure ownership transfer
- IPFS-based document verification
- Permissioned blockchain governance

### Core Blockchain Properties

- Immutable transaction history
- Tamper-resistant records
- Distributed trust across organizations
- Parent-child lineage tracking for land mutation

---

# Development Environment Setup

## Prerequisites

Install the following tools:

- Java 17
- Docker
- Docker Compose
- Git

---

## Ubuntu / Debian Setup

```bash
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk docker.io docker-compose git
```

Set Java environment variables:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
```

Verify installation:

```bash
java -version
```

---

# Running the Blockchain Network

Navigate to the network directory:

```bash
cd blockchain-network
```

Start the Fabric test network:

```bash
./network.sh up createChannel -c landchannel -ca
```

---

# Building the Smart Contract

Navigate to the chaincode directory:

```bash
cd chaincode/land-contract-java
```

Build using the Gradle wrapper:

```bash
./gradlew build
```

---

# Troubleshooting

## Unsupported Class File Major Version

This error occurs if Java 17 is not active.

Fix it by setting:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
```

---

# Future Improvements

- Land mutation engine (split / merge)
- GIS map integration
- Government identity verification
- Multi-state channel federation
- Mobile application for citizens
- Zero-knowledge Aadhaar validation

---

# Contributing

1. Fork the repository
2. Create a new branch

```
git checkout -b feature/your-feature-name
```

3. Commit your changes
4. Submit a Pull Request

---

# License

Licensed under the **Apache License 2.0**.

---

# Acknowledgments

This project explores **blockchain-based governance systems for land administration**, inspired by initiatives such as:

- Digital India Land Records Modernization Programme (DILRMP)
- National ULPIN initiative

The goal is to demonstrate how **permissioned blockchain infrastructure** can enhance **transparency, security, and trust in land ownership systems**.
