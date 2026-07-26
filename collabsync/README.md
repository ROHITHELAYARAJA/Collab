# CollabSync — Real-Time Collaborative Document & Chat Backend

A Spring Boot backend for real-time collaborative document editing with integrated team chat — conceptually similar to "Google Docs + Slack" but backend-only.

## Architecture Overview

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Client    │────▶│ Load Balancer │────▶│  App Instance 1  │
└─────────────┘     └─────────────┘     │  (WebSocket)    │
                                        └───────┬───────┘
                                                │
                    ┌─────────────┐             │ Redis Pub/Sub
                    │   Kafka     │◀────────────┤ (Ephemeral fan-out)
                    │ (Durable)   │             └───────┬───────┘
                    └──────┬──────┘                     │
                           │                     ┌───────┴───────┐
                           ▼                     │  App Instance 2  │
                    ┌─────────────┐             │  (WebSocket)    │
                    │  Database   │             └─────────────────┘
                    │ (PostgreSQL)│
                    └─────────────┘
```

### Core Components

| Component | Technology | Purpose |
|-----------|------------|---------|
| **API/WS Server** | Spring Boot 3.2, Java 17 | Stateless REST + STOMP/WebSocket |
| **Database** | PostgreSQL 16 | System of record (users, workspaces, docs, chat) |
| **Cache/Pub-Sub** | Redis 7 (Lettuce) | Cross-instance WebSocket fan-out, presence, typing indicators |
| **Event Log** | Apache Kafka 3.6 (KRaft) | Durable audit trail for doc ops & chat messages |
| **Auth** | JWT (JJWT 0.12) | Stateless auth with access/refresh tokens |

### Why Kafka *and* Redis?

- **Redis Pub/Sub**: Sub-millisecond fan-out for ephemeral events (cursors, typing, presence). Not durable — if no subscriber, message is lost (by design).
- **Kafka**: Durable, replayable log for document operations and chat messages. Powers persistence consumers, audit, and future analytics.

## Project Structure

```
collabsync/
├── src/main/java/com/collabsync/
│   ├── config/           # Security, WebSocket, Kafka, Redis config
│   ├── controller/       # REST endpoints
│   ├── dto/              # Request/Response DTOs
│   ├── exception/        # Global exception handling
│   ├── model/            # JPA entities
│   ├── ot/               # Operational Transformation engine
│   ├── repository/       # Spring Data JPA repositories
│   ├── security/         # JWT, UserDetails, filters
│   ├── service/          # Business logic
│   └── websocket/        # STOMP controllers, handlers
├── src/main/resources/
│   ├── application.yml   # Config (dev/test/prod profiles)
│   └── db/migration/     # Flyway migrations (future)
├── src/test/java/        # Testcontainers integration tests
├── Dockerfile            # Multi-stage build
├── docker-compose.yml    # Local dev stack (Postgres, Redis, Kafka, App)
└── pom.xml               # Maven build
```

## Getting Started

### Prerequisites

- Docker & Docker Compose
- Java 17+ (for local development without Docker)
- Maven 3.9+

### Quick Start with Docker Compose

```bash
cd collabsync
docker compose up --build
```

This starts:
- **PostgreSQL** on `localhost:5432`
- **Redis** on `localhost:6379`
- **Kafka (KRaft)** on `localhost:9092`
- **App** on `localhost:8080` (health: `http://localhost:8080/actuator/health`)

### Local Development (without Docker for app)

```bash
# Start only infrastructure
docker compose up -d postgres redis kafka

# Run app locally
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## API Reference

### Authentication

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register new user |
| POST | `/api/auth/login` | Login, get access + refresh tokens |
| POST | `/api/auth/refresh` | Refresh access token |
| GET | `/api/auth/me` | Get current user profile |

**Register Request**
```json
{
  "email": "user@example.com",
  "password": "securePassword123",
  "displayName": "John Doe"
}
```

**Auth Response**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "userId": "uuid",
  "email": "user@example.com",
  "displayName": "John Doe",
  "expiresIn": 900000
}
```

### Workspaces

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/workspaces` | List user's workspaces (paginated) |
| POST | `/api/workspaces` | Create workspace |
| GET | `/api/workspaces/{id}` | Get workspace details |
| GET | `/api/workspaces/{id}/members` | List members |
| POST | `/api/workspaces/{id}/members` | Add member (owner/editor) |
| PATCH | `/api/workspaces/{id}/members/{memberId}` | Update member role (owner only) |
| DELETE | `/api/workspaces/{id}/members/{memberId}` | Remove member (owner only) |

**Roles**: `OWNER`, `EDITOR`, `VIEWER`

### Documents

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/documents?workspaceId=` | List documents in workspace |
| POST | `/api/documents?workspaceId=` | Create document (editor/owner) |
| GET | `/api/documents/{id}` | Get document content |
| PUT | `/api/documents/{id}` | Update title (editor/owner) |
| DELETE | `/api/documents/{id}` | Delete document (owner only) |
| GET | `/api/documents/{id}/history` | Get version history (paginated) |

### Chat

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/chat/rooms?workspaceId=` | List chat rooms |
| POST | `/api/chat/rooms` | Create chat room |
| GET | `/api/chat/rooms/{id}/messages` | Get messages (cursor pagination) |
| GET | `/api/chat/rooms/document/{docId}` | Get document-scoped room |

**Cursor Pagination**: Use `?before=msg_id&limit=50` or `?after=msg_id&limit=50`

## WebSocket Protocol

### Connection

```
WS_CONNECT  /ws?accessToken=<JWT>
```

### Document Collaboration

| Direction | Destination | Payload |
|-----------|-------------|---------|
| Client → Server | `/app/doc/{documentId}/edit` | `DocOp` |
| Server → Client | `/topic/doc/{documentId}` | `DocOp` (with serverSeq) |
| Client → Server | `/app/doc/{documentId}/cursor` | `CursorUpdate` |
| Server → Client | `/topic/doc/{documentId}/presence` | `PresenceEvent` |

### Chat

| Direction | Destination | Payload |
|-----------|-------------|---------|
| Client → Server | `/app/chat/{roomId}/send` | `ChatMessage` |
| Server → Client | `/topic/chat/{roomId}` | `ChatMessage` |
| Client → Server | `/app/chat/{roomId}/typing` | `TypingIndicator` |
| Server → Client | `/topic/chat/{roomId}/typing` | `TypingIndicator` |

### Message Envelope

```json
{
  "type": "DOC_OP",
  "documentId": "doc-uuid",
  "payload": { "opType": "INSERT", "position": 42, "content": "hello" },
  "senderId": "user-uuid",
  "clientSeq": 18,
  "serverSeq": 1042,
  "timestamp": "2026-07-25T10:00:00Z"
}
```

**Types**: `DOC_OP`, `CURSOR_UPDATE`, `PRESENCE_JOIN`, `PRESENCE_LEAVE`, `CHAT_MESSAGE`, `TYPING_INDICATOR`, `ACK`, `ERROR`

### Operational Transformation (Phase 6)

- Plain-text insert/delete operations only
- Server-side sequence number per document
- Transform concurrent ops against pending queue
- Tie-break: serverSeq → clientId

## Data Model (Key Tables)

```sql
users (id, email, password_hash, display_name, created_at)
workspaces (id, name, owner_id, created_at)
workspace_members (workspace_id, user_id, role, created_at)  -- UK(workspace_id, user_id)
documents (id, workspace_id, title, content, created_by, created_at, updated_at, version)
document_snapshots (id, document_id, content, version, created_at)
document_ops (id, document_id, op_type, position, content, client_seq, server_seq, author_id, created_at)  -- IDX(document_id, server_seq)
chat_rooms (id, workspace_id, document_id, name, created_at)
chat_messages (id, room_id, author_id, content, created_at)  -- IDX(room_id, created_at)
```

## Running Tests

```bash
# Unit tests (no containers)
./mvnw test -Dtest=*UnitTest

# Integration tests (Testcontainers - requires Docker)
./mvnw test -Dtest=*IntegrationTest
```

Testcontainers spins up real PostgreSQL, Redis, and Kafka containers for each test class.

## Configuration

Key properties in `application.yml`:

```yaml
jwt:
  secret: ${JWT_SECRET:dev-secret-min-32-chars}
  access-token-expiration-ms: 900000      # 15 min
  refresh-token-expiration-ms: 604800000  # 7 days

spring:
  kafka:
    producer:
      acks: all
      enable-idempotence: true
  websocket:
    path: /ws
```

## Build & Deploy

```bash
# Build JAR
./mvnw clean package -DskipTests

# Build Docker image
docker build -t collabsync:latest .

# Run with custom config
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DATABASE_URL=jdbc:postgresql://db:5432/collabsync \
  -e JWT_SECRET=prod-secret-64-chars-minimum \
  collabsync:latest
```

## Architecture Decision Records (ADRs)

| ADR | Decision |
|-----|----------|
| **ADR-001** | STOMP over WebSocket for real-time (simpler than raw WS) |
| **ADR-002** | Kafka + Redis dual pub-sub (durability + low latency) |
| **ADR-003** | Operational Transformation for conflict resolution (not CRDT — simpler for plain text) |
| **ADR-004** | Stateless app instances + Redis fan-out (horizontal scaling) |
| **ADR-005** | JWT with short access + long refresh tokens |
| **ADR-006** | Cursor-based pagination for chat history |

## Interview Talking Points

1. **Why both Kafka and Redis?** Durability vs. latency trade-off. Redis Pub/Sub is fire-and-forget; Kafka is the source of truth.
2. **OT vs CRDT**: OT chosen for plain-text simplicity; CRDT is stretch goal.
3. **Horizontal scaling**: WebSocket connections are sticky per instance; Redis fan-out ensures all instances broadcast.
4. **Failure modes**: Redis down → same-instance clients still work; Kafka down → real-time works but no durability/replay.
5. **Idempotency**: Kafka producer `enable.idempotence=true`; consumer checks `messageId` before persist.
6. **Authorization on every WS message**: JWT validated at handshake + re-checked per message (role changes mid-session).

## License

MIT — for learning and interview preparation purposes.