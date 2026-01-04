# MiniRPC

[中文说明](README_CN.md)

MiniRPC is a lightweight, modular RPC framework built with Java 21 and Netty. It focuses on clear protocol framing,
pluggable components, and a small, testable core so each layer can evolve independently.

## Overview
- Custom binary protocol with fixed header + extensible header + body
- Netty-based transport pipeline and framing
- Pluggable serialization, registry, load balancing, and governance hooks
- Runnable provider/consumer examples for verification

## Status
This repository tracks the E1/Beta implementation. See `docs/progress_log.md` for current progress and decisions.

## Quick Start
Build all example dependencies:
```bash
mvn -pl minirpc-example-provider,minirpc-example-consumer -am package
```

Run the entry points from your IDE:
- Provider: `top.ainexur.minirpc.example.provider.Main` (arg: port, default 8080)
- Consumer: `top.ainexur.minirpc.example.consumer.Main` (args: host, port)

Start the provider first, then run the consumer to see a request/response round-trip.

CLI run (no exec plugin required):
```bash
mvn -pl minirpc-example-provider -am -DskipTests package dependency:copy-dependencies
java -cp "minirpc-example-provider/target/classes:minirpc-example-provider/target/dependency/*" \
  top.ainexur.minirpc.example.provider.Main
```

```bash
mvn -pl minirpc-example-consumer -am -DskipTests package dependency:copy-dependencies
java -cp "minirpc-example-consumer/target/classes:minirpc-example-consumer/target/dependency/*" \
  top.ainexur.minirpc.example.consumer.Main 127.0.0.1 8080
```

## Requirements
- JDK 21
- Maven 3.8+

## Building
```bash
mvn -DskipTests package
```

## Testing
```bash
mvn test
```

Run a specific module:
```bash
mvn -pl minirpc-protocol test
```

## Configuration Notes
- Default provider port: 8080
- Consumer arguments: `host` `port` (defaults to `127.0.0.1 8080`)
- Examples run without registry; registry and load balancer modules are not wired into the demo flow.

## Protocol Header (Fixed 22 bytes)
- `magic` (2 bytes)
- `version` (1 byte)
- `serializeType` (1 byte)
- `flags` (2 bytes)
- `requestId` (8 bytes)
- `headerLen` (4 bytes)
- `bodyLen` (4 bytes)

## Module Status
| Module | Status | Notes |
| --- | --- | --- |
| minirpc-protocol | Implemented | Frame encode/decode and validation |
| minirpc-serialization | Implemented | JSON + SPI registry |
| minirpc-transport-netty | Implemented | Netty transport + slicing |
| minirpc-core | Implemented | Exporter, dispatcher, proxy |
| minirpc-registry-redis | In progress | Registry design present |
| minirpc-loadbalancer | In progress | Strategy placeholders |
| minirpc-governance | In progress | Filter chain hooks |
| minirpc-example-* | Implemented | Runnable demos |
| minirpc-poc | Experimental | Experiments and notes |

## Project Layout
- `minirpc-common`: shared utilities and errors
- `minirpc-protocol`: frame encoding/decoding and protocol definitions
- `minirpc-serialization`: serializer SPI and JSON implementation
- `minirpc-transport-netty`: Netty transport, frame slicing, and message codecs
- `minirpc-registry-redis`: Redis registry (TTL + Pub/Sub design)
- `minirpc-loadbalancer`: load balancing strategies
- `minirpc-governance`: filter chain for timeout/retry/trace hooks
- `minirpc-core`: service export, invocation, and client proxy
- `minirpc-example-provider`: provider demo
- `minirpc-example-consumer`: consumer demo
- `minirpc-poc`: proof-of-concept experiments

## Documentation
- `docs/chatgpt/03架构设计/` (architecture overview and design)
- `docs/chatgpt/06_dev_ready/` (code blueprint and test plan)

## Contributing
Issues and PRs are welcome. Keep changes modular and add tests when behavior changes.

## License
No license yet. Intended for learning and internal use only.
