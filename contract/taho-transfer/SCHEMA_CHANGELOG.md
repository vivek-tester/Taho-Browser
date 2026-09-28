# Taho Transfer Contract Schema Changelog

## [1.0.0] - 2026-09-28

### Initial Release (`taho.request-transfer` v1)
- **Schema ID**: `https://taho.local/contracts/request-transfer-v1.json`
- **Schema Version**: 1
- **Minimum Reader Version**: 1

#### Core Contract Objects
- **`TransferEnvelope`**: Root container ensuring immutable provenance and cryptographic/ULID identity (`transferId`).
- **`TransferSource`**: Identifies originating product (`taho-browser`), runtime engine (`geckoview-*`), and session attribution bindings.
- **`TransferRequest`**: Captures method, canonical URL, query parameter models with redaction flags, normalized request headers, and request body.
- **`TransferBody`**: Supports multiple representations (`TEXT`, `JSON`, `FORM`, `MULTIPART`, `GRAPHQL`, `BINARY`) and encoding channels (`UTF8`, `BASE64`, `FILE_URI`).
- **`TransferCapture`**: Records timing evidence (`ttfbMs`, `totalMs`), HTTP status code, initiator (`SCRIPT`, `DOCUMENT`), and granular 7-field completeness matrix.
- **`TransferSecurity`**: Carries secret policy (`PARAMETERIZE`, `MASK`, `EXPLICIT`), secret count, findings taxonomy, and private session indicator.
- **`TransferProvenance`**: Normalizer version, capture engine version, timestamp, and redirect count.

#### Invariants & Forward-Compatibility
- Strict property typing on required fields.
- Open extension (`additionalProperties: true` where future optional metadata may be appended without breaking reader v1).
- Reader rejects envelopes where `minimumReaderVersion > 1`.
