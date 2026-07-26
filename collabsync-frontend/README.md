# CollabSync Frontend

Real-time collaborative document editor and chat client for the CollabSync backend.

## Tech Stack

- **React 18** - UI framework
- **Vite** - Build tooling and dev server
- **React Router v6** - Client-side routing
- **Native WebSocket + STOMP** - Real-time communication (no external socket libraries)
- **Plain CSS with CSS Variables** - Styling (no Tailwind, no component library)

## Project Structure

```
src/
├── main.jsx              # App entry point
├── App.jsx               # Root component with routing
├── index.css             # Global styles (CSS variables + utility classes)
├── context/
│   ├── AuthContext.jsx   # Authentication state (JWT, user, login/register)
│   └── SocketContext.jsx # WebSocket connection (STOMP, reconnect, subscriptions)
├── pages/
│   ├── LoginPage.jsx     # Login form
│   ├── RegisterPage.jsx  # Registration form
│   ├── WorkspaceListPage.jsx  # List workspaces, create new
│   ├── DocumentListPage.jsx   # List documents in workspace
│   └── EditorPage.jsx    # Collaborative editor + chat + presence
└── components/
    └── ConnectionBanner.jsx # Reconnect status indicator
```

## Getting Started

### Prerequisites

- Node.js 18+
- CollabSync backend running on `http://localhost:8080`

### Installation

```bash
cd collabsync-frontend
npm install
```

### Development

```bash
npm run dev
```

The dev server runs on `http://localhost:3000` with proxy to backend API at `http://localhost:8080`.

### Build

```bash
npm run build
```

Output goes to `dist/`.

## Architecture Highlights

### Single WebSocket Connection (SocketContext)

The `SocketProvider` establishes **one** WebSocket connection when authenticated and shares it via Context. Components subscribe to destinations:

```jsx
const { subscribe, send } = useSocket()

useEffect(() => {
  const unsubscribe = subscribe('/topic/doc/123', (msg) => {
    if (msg.type === 'DOC_OP') applyRemoteOp(msg.payload)
  })
  return unsubscribe
}, [subscribe])
```

### STOMP Protocol

Uses STOMP framing over WebSocket (matching backend's Spring STOMP setup):
- `CONNECT` with JWT in Authorization header
- `SUBSCRIBE` to `/topic/*` for broadcasts, `/user/queue/*` for user-specific
- `SEND` to `/app/*` for client → server messages
- Heartbeat: 10s client → server, 10s server → client
- Automatic reconnect with exponential backoff (1s → 30s cap)

### Optimistic Editor Updates

The editor applies local keystrokes **immediately** before server confirmation:
1. User types → local state updates instantly
2. Diff computed → operation sent to server with `clientSeq`
3. Server responds with `ACK` containing `serverSeq`
4. Pending operation removed from local queue

If connection drops, pending ops are queued and flushed on reconnect.

### Remote Operation Reconciliation

When a `DOC_OP` arrives from another collaborator:
1. Transform the remote op against any unacknowledged local ops (client-side OT)
2. Apply transformed op to local text state
3. Adjust local cursor position by same transform

This prevents cursor jump and text corruption during concurrent edits.

### Presence & Cursors

- `PRESENCE_JOIN`/`PRESENCE_LEAVE` → presence bar avatars
- `CURSOR_UPDATE` (throttled 50ms) → remote cursor markers with name labels

## API Integration

All REST calls go through `apiCall()` in `AuthContext` which:
- Attaches `Authorization: Bearer <token>`
- Auto-refreshes token on 401 using `/api/auth/refresh`
- Redirects to `/login` if refresh fails

## Keyboard Shortcuts

| Key | Action |
|-----|--------|
| Enter (chat) | Send message |
| Ctrl/Cmd+S | (Future) Save document |

## Browser Support

Modern browsers with WebSocket support (Chrome 4+, Firefox 4+, Safari 5+, Edge 12+).

## Production Notes

- Set `VITE_API_URL` env var for production API endpoint
- Build with `npm run build` → deploy `dist/` to static hosting (Netlify, Vercel, S3+CloudFront)
- Ensure backend CORS allows your production domain