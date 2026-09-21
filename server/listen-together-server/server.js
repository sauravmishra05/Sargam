/**
 * Sargam Listen Together / Sync Play WebSocket Server
 * Handles room creation, short room codes, max 10 devices per room,
 * host action relay, clock sync timestamps, and buffer management.
 */

const WebSocket = require('ws');
const protobuf = require('protobufjs');
const zlib = require('zlib');
const http = require('http');

const PORT = process.env.PORT || 8080;
const MAX_USERS_PER_ROOM = 10;

// Embedded protobuf definitions matching listentogether.proto
const protoSchema = `
syntax = "proto3";
package com.metrolist.music.listentogether.proto;

message Envelope {
  string type = 1;
  bytes payload = 2;
  bool compressed = 3;
}

message TrackInfo {
  string id = 1;
  string title = 2;
  string artist = 3;
  string album = 4;
  int64 duration = 5;
  string thumbnail = 6;
  string suggestedBy = 7;
}

message UserInfo {
  string userId = 1;
  string username = 2;
  bool isHost = 3;
  bool isConnected = 4;
}

message RoomState {
  string roomCode = 1;
  string hostId = 2;
  repeated UserInfo users = 3;
  TrackInfo currentTrack = 4;
  bool isPlaying = 5;
  int64 position = 6;
  int64 lastUpdate = 7;
  float volume = 8;
  repeated TrackInfo queue = 9;
}

message CreateRoomPayload {
  string username = 1;
}

message JoinRoomPayload {
  string roomCode = 1;
  string username = 2;
}

message ApproveJoinPayload {
  string userId = 1;
}

message RejectJoinPayload {
  string userId = 1;
  string reason = 2;
}

message PlaybackActionPayload {
  string action = 1;
  string trackId = 2;
  int64 position = 3;
  TrackInfo trackInfo = 4;
  bool insertNext = 5;
  repeated TrackInfo queue = 6;
  string queueTitle = 7;
  float volume = 8;
  int64 serverTime = 9;
}

message BufferReadyPayload {
  string trackId = 1;
}

message KickUserPayload {
  string userId = 1;
  string reason = 2;
}

message RoomCreatedPayload {
  string roomCode = 1;
  string userId = 2;
  string sessionToken = 3;
}

message JoinRequestPayload {
  string userId = 1;
  string username = 2;
}

message JoinApprovedPayload {
  string roomCode = 1;
  string userId = 2;
  string sessionToken = 3;
  RoomState state = 4;
}

message JoinRejectedPayload {
  string reason = 1;
}

message UserJoinedPayload {
  string userId = 1;
  string username = 2;
}

message UserLeftPayload {
  string userId = 1;
  string username = 2;
}

message BufferWaitPayload {
  string trackId = 1;
  repeated string waitingFor = 2;
}

message BufferCompletePayload {
  string trackId = 1;
}

message ErrorPayload {
  string code = 1;
  string message = 2;
}

message HostChangedPayload {
  string newHostId = 1;
  string newHostName = 2;
}

message KickedPayload {
  string reason = 1;
}

message SyncStatePayload {
  TrackInfo currentTrack = 1;
  bool isPlaying = 2;
  int64 position = 3;
  int64 lastUpdate = 4;
  repeated TrackInfo queue = 5;
  float volume = 6;
}
`;

// Parse Proto Root
const root = protobuf.parse(protoSchema).root;
const Envelope = root.lookupType('com.metrolist.music.listentogether.proto.Envelope');

function createPayloadType(typeName) {
  return root.lookupType(`com.metrolist.music.listentogether.proto.${typeName}`);
}

const types = {
  Envelope,
  CreateRoomPayload: createPayloadType('CreateRoomPayload'),
  JoinRoomPayload: createPayloadType('JoinRoomPayload'),
  ApproveJoinPayload: createPayloadType('ApproveJoinPayload'),
  RejectJoinPayload: createPayloadType('RejectJoinPayload'),
  PlaybackActionPayload: createPayloadType('PlaybackActionPayload'),
  BufferReadyPayload: createPayloadType('BufferReadyPayload'),
  KickUserPayload: createPayloadType('KickUserPayload'),
  RoomCreatedPayload: createPayloadType('RoomCreatedPayload'),
  JoinRequestPayload: createPayloadType('JoinRequestPayload'),
  JoinApprovedPayload: createPayloadType('JoinApprovedPayload'),
  JoinRejectedPayload: createPayloadType('JoinRejectedPayload'),
  UserJoinedPayload: createPayloadType('UserJoinedPayload'),
  UserLeftPayload: createPayloadType('UserLeftPayload'),
  BufferWaitPayload: createPayloadType('BufferWaitPayload'),
  BufferCompletePayload: createPayloadType('BufferCompletePayload'),
  ErrorPayload: createPayloadType('ErrorPayload'),
  HostChangedPayload: createPayloadType('HostChangedPayload'),
  KickedPayload: createPayloadType('KickedPayload'),
  SyncStatePayload: createPayloadType('SyncStatePayload')
};

function encodeMessage(type, payloadObj) {
  let payloadBytes = new Uint8Array(0);
  if (payloadObj && types[type + 'Payload']) {
    const payloadType = types[type + 'Payload'];
    const errMsg = payloadType.verify(payloadObj);
    if (errMsg) console.warn(`Validation warning for ${type}: ${errMsg}`);
    const message = payloadType.create(payloadObj);
    payloadBytes = payloadType.encode(message).finish();
  } else if (payloadObj && types[type]) {
    const payloadType = types[type];
    const message = payloadType.create(payloadObj);
    payloadBytes = payloadType.encode(message).finish();
  }

  const envelopeObj = {
    type,
    payload: payloadBytes,
    compressed: false
  };

  const envelopeMsg = Envelope.create(envelopeObj);
  return Envelope.encode(envelopeMsg).finish();
}

function decodeMessage(buffer) {
  try {
    const envelope = Envelope.decode(buffer);
    let payloadBytes = envelope.payload;
    if (envelope.compressed) {
      payloadBytes = zlib.gunzipSync(payloadBytes);
    }
    const type = envelope.type;
    let payloadObj = null;

    const payloadType = types[type + 'Payload'] || types[type];
    if (payloadType && payloadBytes && payloadBytes.length > 0) {
      payloadObj = payloadType.toObject(payloadType.decode(payloadBytes), { defaults: true });
    }

    return { type, payload: payloadObj };
  } catch (err) {
    console.error('Failed to decode buffer:', err.message);
    return null;
  }
}

// In-Memory Rooms State
const rooms = new Map(); // roomCode -> Room

function generateRoomCode() {
  let code;
  do {
    code = Math.floor(100000 + Math.random() * 900000).toString();
  } while (rooms.has(code));
  return code;
}

function generateId() {
  return Math.random().toString(36).substring(2, 10);
}

const server = http.createServer((req, res) => {
  res.writeHead(200, { 'Content-Type': 'text/plain' });
  res.end('Sargam Listen Together Relay Server Running');
});

const wss = new WebSocket.Server({ server });

wss.on('connection', (ws) => {
  ws.id = generateId();
  ws.roomCode = null;
  ws.username = 'Guest';

  ws.on('message', (data) => {
    const msg = decodeMessage(data);
    if (!msg) return;

    const { type, payload } = msg;

    switch (type) {
      case 'create_room': {
        const roomCode = generateRoomCode();
        const userId = ws.id;
        const sessionToken = generateId();
        ws.username = payload?.username || 'Host';
        ws.roomCode = roomCode;

        const room = {
          code: roomCode,
          hostId: userId,
          users: new Map(),
          currentTrack: null,
          isPlaying: false,
          position: 0,
          lastUpdate: Date.now(),
          volume: 1.0,
          queue: [],
          bufferState: new Map()
        };

        room.users.set(userId, {
          userId,
          username: ws.username,
          isHost: true,
          isConnected: true,
          ws
        });

        rooms.set(roomCode, room);

        ws.send(encodeMessage('room_created', {
          roomCode,
          userId,
          sessionToken
        }));
        console.log(`Room created: ${roomCode} by ${ws.username}`);
        break;
      }

      case 'join_room': {
        const roomCode = payload?.roomCode;
        const username = payload?.username || 'Member';
        const room = rooms.get(roomCode);

        if (!room) {
          ws.send(encodeMessage('join_rejected', { reason: 'Room not found' }));
          return;
        }

        if (room.users.size >= MAX_USERS_PER_ROOM) {
          ws.send(encodeMessage('join_rejected', { reason: 'Room is full (max 10 devices)' }));
          return;
        }

        ws.username = username;
        ws.roomCode = roomCode;
        const userId = ws.id;
        const sessionToken = generateId();

        room.users.set(userId, {
          userId,
          username,
          isHost: false,
          isConnected: true,
          ws
        });

        // Send approval to new member with current room state
        const usersList = Array.from(room.users.values()).map(u => ({
          userId: u.userId,
          username: u.username,
          isHost: u.isHost,
          isConnected: u.isConnected
        }));

        ws.send(encodeMessage('join_approved', {
          roomCode,
          userId,
          sessionToken,
          state: {
            roomCode,
            hostId: room.hostId,
            users: usersList,
            currentTrack: room.currentTrack,
            isPlaying: room.isPlaying,
            position: room.position,
            lastUpdate: room.lastUpdate,
            volume: room.volume,
            queue: room.queue
          }
        }));

        // Broadcast to existing users that someone joined
        for (const user of room.users.values()) {
          if (user.userId !== userId && user.ws.readyState === WebSocket.OPEN) {
            user.ws.send(encodeMessage('user_joined', {
              userId,
              username
            }));
          }
        }
        console.log(`User ${username} joined room ${roomCode}`);
        break;
      }

      case 'playback_action': {
        const room = rooms.get(ws.roomCode);
        if (!room) return;

        // Ensure host or allowed user controls playback
        const user = room.users.get(ws.id);
        if (!user || !user.isHost) return;

        const serverTime = Date.now();
        const action = payload?.action;

        if (action === 'play') {
          room.isPlaying = true;
          if (payload.position !== undefined) room.position = payload.position;
          room.lastUpdate = serverTime;
        } else if (action === 'pause') {
          room.isPlaying = false;
          if (payload.position !== undefined) room.position = payload.position;
          room.lastUpdate = serverTime;
        } else if (action === 'seek') {
          if (payload.position !== undefined) room.position = payload.position;
          room.lastUpdate = serverTime;
        } else if (action === 'change_track') {
          if (payload.trackInfo) room.currentTrack = payload.trackInfo;
          if (payload.queue) room.queue = payload.queue;
          room.isPlaying = false;
          room.position = 0;
          room.lastUpdate = serverTime;
        } else if (action === 'sync_queue') {
          if (payload.queue) room.queue = payload.queue;
        } else if (action === 'set_volume') {
          if (payload.volume !== undefined) room.volume = payload.volume;
        }

        const outgoingPayload = {
          ...payload,
          serverTime
        };

        // Broadcast action to all room members
        for (const u of room.users.values()) {
          if (u.ws.readyState === WebSocket.OPEN) {
            u.ws.send(encodeMessage('sync_playback', outgoingPayload));
          }
        }
        break;
      }

      case 'buffer_ready': {
        const room = rooms.get(ws.roomCode);
        if (!room) return;

        const trackId = payload?.trackId;
        room.bufferState.set(ws.id, trackId);

        // Check if all connected members are ready
        let allReady = true;
        for (const u of room.users.values()) {
          if (u.isConnected && room.bufferState.get(u.userId) !== trackId) {
            allReady = false;
            break;
          }
        }

        if (allReady) {
          // Notify room that buffering is complete
          for (const u of room.users.values()) {
            if (u.ws.readyState === WebSocket.OPEN) {
              u.ws.send(encodeMessage('buffer_complete', { trackId }));
            }
          }
        }
        break;
      }

      case 'ping': {
        ws.send(encodeMessage('pong', { serverTime: Date.now() }));
        break;
      }

      case 'leave_room': {
        handleUserLeave(ws);
        break;
      }
    }
  });

  ws.on('close', () => {
    handleUserLeave(ws);
  });
});

function handleUserLeave(ws) {
  if (!ws.roomCode) return;
  const room = rooms.get(ws.roomCode);
  if (!room) return;

  const leavingUser = room.users.get(ws.id);
  room.users.delete(ws.id);

  if (room.users.size === 0) {
    rooms.delete(ws.roomCode);
    console.log(`Room ${ws.roomCode} destroyed (empty)`);
  } else {
    // If host left, assign new host
    if (leavingUser && leavingUser.isHost) {
      const newHost = room.users.values().next().value;
      if (newHost) {
        newHost.isHost = true;
        room.hostId = newHost.userId;
        for (const u of room.users.values()) {
          if (u.ws.readyState === WebSocket.OPEN) {
            u.ws.send(encodeMessage('host_changed', {
              newHostId: newHost.userId,
              newHostName: newHost.username
            }));
          }
        }
      }
    }

    // Broadcast user left
    for (const u of room.users.values()) {
      if (u.ws.readyState === WebSocket.OPEN) {
        u.ws.send(encodeMessage('user_left', {
          userId: ws.id,
          username: ws.username
        }));
      }
    }
  }

  ws.roomCode = null;
}

server.listen(PORT, () => {
  console.log(`Listen Together Server running on port ${PORT}`);
});
