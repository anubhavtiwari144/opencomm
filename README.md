# OpenComm

OpenComm is a no-login communication prototype built with React.

The host generates a private invite link and shares it with one friend. The
friend opens the link, requests to join, and the host must accept before the chat
unlocks. Each room allows only one guest.

## Available Scripts

### `npm start`

Runs the app in development mode.

### `npm run build`

Builds the app for production.

## Prototype Note

This version stores rooms in the browser using `localStorage` and uses
`BroadcastChannel` so the flow works across tabs in the same browser. A real
multi-device version will need a backend or realtime service.

## Backend

The `backend/` folder contains a Spring Boot WebSocket backend for the real
OpenComm flow described in the architecture document.

It exposes:

- `POST /api/rooms` to create a host room
- `GET /api/rooms/{roomId}` to validate an invite
- `DELETE /api/rooms/{roomId}` to end a room
- `/ws` as the STOMP WebSocket endpoint
- `/app/join`, `/app/accept`, `/app/reject`, `/app/send`, `/app/end`
- `/topic/room/{roomId}` for chat messages
- `/topic/room/{roomId}/status` for room lifecycle events

The frontend should create and store an anonymous browser `sessionId`, then pass
it in REST payloads and in the STOMP connect header `client-session-id`.

To run it with the included Maven Wrapper:

```bash
cd backend
./mvnw spring-boot:run
```

On Windows PowerShell:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```
