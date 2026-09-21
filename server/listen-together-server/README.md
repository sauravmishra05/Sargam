# Sargam Listen Together Relay Server

WebSocket relay server for real-time room creation, short room code matching, host action broadcasting, and clock synchronization for Sargam Listen Together / Sync Play.

## Features
- 6-digit short room codes (e.g. `482910`).
- Up to 10 connected devices per room limit enforcement.
- Protocol Buffers framing using `listentogether.proto` envelopes.
- Server timestamp injection (`serverTime`) for clock drift correction across networks.
- No database or authentication required.

## Running Locally

```bash
npm install
npm start
```

The server listens on port `8080` (or the port specified by the `PORT` environment variable).

## Docker Deployment

```bash
docker build -t sargam-listen-together .
docker run -d -p 8080:8080 sargam-listen-together
```
