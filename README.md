# mini-rpc

[中文说明](README_CN.md)

MiniRPC is a Dubbo-lite style, lightweight RPC framework built with Java 21 + Netty. The project focuses on a modular
design that makes each layer (protocol, transport, registry, governance) independent and easy to iterate.

## Background
MiniRPC is designed to explore and implement core RPC concepts, including custom protocol framing, serialization
choices, service discovery, and governance. The architecture is split into Maven modules so each capability can be
understood, replaced, and tested in isolation.

## Goals
- Keep protocol framing small and explicit
- Support pluggable serialization, load balancing, and registry
- Provide runnable examples for verification

## Key Capabilities
- Custom binary protocol (fixed header + extensible header + body)
- Netty-based transport pipeline
- Redis-backed registry module (TTL + Pub/Sub design)
- Filter-based governance chain (timeout/retry/trace)
- Example provider/consumer modules

## Architecture Overview
Consumer → Filter → LoadBalancer → Netty Client → Protocol Encoder → TCP → Netty Server → Protocol Decoder → Invoker

## Modules
- minirpc-common
- minirpc-protocol
- minirpc-serialization
- minirpc-transport-netty
- minirpc-registry-redis
- minirpc-loadbalancer
- minirpc-governance
- minirpc-core
- minirpc-example-provider
- minirpc-example-consumer
- minirpc-poc

## Requirements
- JDK 21
- Maven 3.8+

## Build
```bash
mvn -q -DskipTests package
```

## Test
```bash
mvn test
```

## Documentation
- docs/chatgpt/03架构设计/ (architecture overview and full design)
- docs/chatgpt/06_dev_ready/ (code blueprint and test plan)

## Roadmap
- Complete request/response codec
- Add transport integration tests
- Improve observability and governance hooks

## Contributing
Issues and PRs are welcome. Keep changes modular and add tests where appropriate.

## License
TBD
