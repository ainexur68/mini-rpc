# MiniRPC Protocol Specification (PROTOCOL.md, Draft)

## 1. Overview

MiniRPC defines a custom binary framing protocol for RPC communication between Consumer and Provider.
The protocol is designed with the following goals:

- Simple enough to understand and implement
- Efficient in network usage and parsing
- Extensible for future features (compression, encryption, tracing, metadata)
- Backward and forward compatible at the framing level

A MiniRPC frame consists of:

```text
+---------------------------+
| Fixed Header              | 22 bytes
+---------------------------+
| Extensible Header         | HeaderLength bytes (may be 0)
+---------------------------+
| Body                      | BodyLength bytes
+---------------------------+
```

All multi-byte integer fields use **big-endian** byte order.

---

## 2. Fixed Header (22 bytes)

| Field         | Size (bytes) | Type  | Description                                                                     |
| ------------- | ------------ | ----- | ------------------------------------------------------------------------------- |
| Magic         | 2            | short | Magic number to identify MiniRPC frames. Fixed to `0xCAFE`.                     |
| Version       | 1            | byte  | Protocol version. MiniRPC 1.0 uses value `1`.                                   |
| SerializeType | 1            | byte  | Serialization type identifier. Supports multiple implementations.               |
| Flags         | 2            | short | Bit flags describing frame attributes (heartbeat, compressed, encrypted, etc.). |
| RequestId     | 8            | long  | Unique ID for correlating Request and Response.                                 |
| HeaderLength  | 4            | int   | Length of Extensible Header in bytes. Can be 0.                                 |
| BodyLength    | 4            | int   | Length of Body in bytes. Can be 0.                                              |

### 2.1 Magic

- Constant value: `0xCAFE`
- Decoder must validate Magic before further parsing.
- If Magic is invalid, the frame should be discarded and the connection may be closed.

### 2.2 Version

- MiniRPC 1.0 uses `1`.
- Future protocol versions may introduce new semantics.
- For MiniRPC 1.0 implementation, any non-`1` version may be treated as incompatible.

---

## 3. SerializeType

`SerializeType` is a 1-byte unsigned value (`0-255`) representing the serialization algorithm for the Body.

MiniRPC 1.0 defines the following mapping:

| Value   | Name         | Description                                            |
| ------- | ------------ | ------------------------------------------------------ |
| 0       | JSON         | Default serialization. Human-readable, easy to debug.  |
| 1       | Kryo         | Optional high-performance binary serialization.        |
| 2-127   | Reserved     | Reserved for future built-in types.                    |
| 128-255 | User-defined | Reserved for user-defined serialization types via SPI. |

### 3.1 Serialization Interface

All serialization implementations MUST implement a common interface (for example):

```java
public interface Serializer {
    byte getSerializeType();      // e.g. 0 for JSON, 1 for Kryo
    byte[] serialize(Object obj);
    <T> T deserialize(byte[] data, Class<T> type);
}
```

Requirements:

- `getSerializeType()` must match the value written into the frame's `SerializeType` field.
- Both Consumer and Provider MUST use compatible serializers (same type id and implementation).
- On unknown `SerializeType`, the framework must:
  - Not crash
  - Return a clear error (e.g. `UNSUPPORTED_SERIALIZE_TYPE`)
  - Log the problem for debugging

---

## 4. Flags (16-bit bitmap)

`Flags` is a 2-byte field interpreted as a bit bitmap:

```text
bit0: heartbeat frame        (1 = heartbeat)
bit1: compressed body        (1 = body is compressed)
bit2: encrypted body         (1 = body is encrypted)
bit3: one-way request        (1 = one-way, no response expected)
bit4: response frame         (1 = Response, 0 = Request)
bit5-bit15: reserved         (must be 0 in MiniRPC 1.0)
```

MiniRPC 1.0 requirements:

- `bit4` MUST be correctly set and interpreted to distinguish Request vs Response.
- Heartbeat frames MAY reuse the same protocol format with `bit0 = 1`.
- Compression/Encryption bits MAY remain `0` in 1.0, but the field is frozen for future use.

Example (Java-style):

```java
public final class FlagBits {
    public static final int HEARTBEAT = 1 << 0;
    public static final int COMPRESSED = 1 << 1;
    public static final int ENCRYPTED = 1 << 2;
    public static final int ONE_WAY = 1 << 3;
    public static final int RESPONSE = 1 << 4;
}
```

---

## 5. Extensible Header (Ext Header)

`HeaderLength` specifies the total length of the Ext Header region.

- `HeaderLength == 0` → no Ext Header present.
- `HeaderLength > 0` → Ext Header occupies the next `HeaderLength` bytes after the Fixed Header.

MiniRPC 1.0 framing rules:

- Protocol structure (Fixed Header + Ext Header + Body) is **frozen**.
- Implementation may **not** use Ext Header in 1.0 (set `HeaderLength = 0`), but decoder MUST still support skipping it.
- Future versions may define concrete formats for Ext Header (e.g., JSON map or binary key-value).

Typical future use cases:

- Distributed tracing: `traceId`, `spanId`, `parentSpanId`
- Routing metadata: `serviceGroup`, `version`, `region`
- Custom user metadata: labels, tags, tenant info

Recommended future encoding strategies:

- Simple: JSON string encoded as UTF-8 bytes.
- Advanced: binary KV `[KeyLen][Key][ValLen][Val]` sequences.

Decoder requirement:

- If `HeaderLength > 0` but current version does not understand the format, it MUST safely skip `HeaderLength` bytes.

---

## 6. Body

The Body contains the serialized form of either:

- An RPC Request (`bit4 == 0` in Flags), or
- An RPC Response (`bit4 == 1` in Flags).

### 6.1 Request Body

Typically contains fields like:

- interfaceName
- methodName
- parameterTypes
- arguments
- attachments (optional metadata)

The exact structure is defined by the Java-side `RpcRequest` class and the chosen `Serializer`.

### 6.2 Response Body

Typically contains fields like:

- requestId
- result (if success)
- errorCode / errorMessage (if failure)
- attachments (optional metadata)

The exact structure is defined by the Java-side `RpcResponse` class and the chosen `Serializer`.

---

## 7. Decoding Process

A robust decoder should follow these steps:

1. Ensure at least 22 readable bytes.
2. Read Fixed Header fields in the exact order:
   - Magic (2)
   - Version (1)
   - SerializeType (1)
   - Flags (2)
   - RequestId (8)
   - HeaderLength (4)
   - BodyLength (4)
3. Validate Magic and Version.
4. Ensure readable bytes ≥ `HeaderLength + BodyLength`.
5. Skip `HeaderLength` bytes (Ext Header).
6. Read `BodyLength` bytes into a buffer.
7. Use `SerializeType` to lookup the corresponding `Serializer`.
8. Deserialize into either `RpcRequest` or `RpcResponse` based on `Flags.RESPONSE` bit.

Error handling:

- Invalid Magic → drop frame, may close connection.
- Unsupported Version → may close connection or respond with error.
- Unsupported SerializeType → return error response (if possible) and log.
- Incomplete frame → wait for more bytes (Netty-style cumulation).

---

## 8. Encoding Process

For encoding a Request/Response frame:

1. Serialize the `RpcRequest` or `RpcResponse` object using the selected `Serializer`.
2. Prepare Ext Header bytes (if any). In MiniRPC 1.0 this can be empty.
3. Compute:
   - `headerLength = extHeaderBytes.length`
   - `bodyLength = bodyBytes.length`
4. Write Fixed Header in order:
   - Magic
   - Version
   - SerializeType
   - Flags
   - RequestId
   - HeaderLength
   - BodyLength
5. Write Ext Header bytes (if `headerLength > 0`).
6. Write Body bytes.

---

## 9. Compatibility and Evolution

The protocol is designed to allow safe evolution:

- Adding new semantics in Ext Header does not break older decoders, as long as they correctly skip `HeaderLength` bytes.
- New `SerializeType` values can be introduced without changing the framing.
- New `Flags` bits can be defined as long as older versions treat unknown bits as 0.

**MiniRPC 1.0 implementation requirements:**

- MUST strictly follow the framing rules in this document.
- MUST correctly handle `HeaderLength = 0` and `HeaderLength > 0` (skipping).
- MUST handle unknown `SerializeType` gracefully (no crash).
- SHOULD log enough information for debugging protocol-level issues.

## 10. Error Code Specification

MiniRPC defines unified error codes for protocol-level and RPC-level failures:

| Code | Name                       | Description                         |
| ---- | -------------------------- | ----------------------------------- |
| 0    | OK                         | Success                             |
| 1    | TIMEOUT                    | Request timed out                   |
| 2    | SERIALIZE_ERROR            | Serialization failed                |
| 3    | DESERIALIZE_ERROR          | Deserialization failed              |
| 4    | UNSUPPORTED_SERIALIZE_TYPE | Unknown SerializeType               |
| 5    | INTERNAL_SERVER_ERROR      | Provider threw unexpected exception |
| 6    | PROTOCOL_ERROR             | Malformed protocol frame            |
| 7    | CONNECTION_CLOSED          | Connection closed unexpectedly      |

These values appear in `RpcResponse.errorCode`.

## 11. Example Frame Hex Dump

Example Request Frame (JSON, no ExtHeader):

```
CA FE                                        # Magic
01                                           # Version
00                                           # SerializeType = JSON
00 00                                        # Flags = 0
00 00 00 00 00 00 00 01                      # RequestId = 1
00 00 00 00                                  # HeaderLength = 0
00 00 00 17                                  # BodyLength = 23

7B 22 6D 65 74 68 6F 64 22 3A 22 68          # Body bytes (JSON): {"method":"hello"}
65 6C 6C 6F 22 7D
```

(Actual JSON depends on the RpcRequest content.)
