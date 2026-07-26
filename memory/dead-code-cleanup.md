---
name: dead-code-cleanup
description: Delete duplicate configuration files that are not active.
metadata:
  type: project
---
The project contains several duplicate configuration classes where one is active (annotated) and others are dead code (annotations commented out).
**Why:** Improves codebase maintainability and prevents confusion.
**How to apply:** Delete:
- `collabsync/src/main/java/com/collabsync/security/SecurityConfig.java`
- `collabsync/src/main/java/com/collabsync/websocket/WebSocketConfig.java`
- `collabsync/src/main/java/com/collabsync/config/RedisMessageListener.java` (The one in `com.collabsync.service` is the active one).