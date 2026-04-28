import { useEffect, useMemo, useRef, useState } from 'react';
import './App.css';

const API_BASE = process.env.REACT_APP_API_BASE || 'http://localhost:8080';
const SESSION_KEY = 'opencomm-session-id';

const flowSteps = [
  {
    id: 'create',
    label: 'Create room',
    detail: 'Host starts a private room and gets one invite link.',
  },
  {
    id: 'invite',
    label: 'Share invite',
    detail: 'The friend opens the link and asks to join.',
  },
  {
    id: 'approve',
    label: 'Approve guest',
    detail: 'Host accepts one pending guest before messages unlock.',
  },
  {
    id: 'chat',
    label: 'Chat privately',
    detail: 'Only the host and accepted guest can use the room.',
  },
];

// const quickReplies = [
//   'I can see your messages.',
//   'Let us continue here.',
//   'This room feels private.',
// ];

function getSessionId() {
  const existing = localStorage.getItem(SESSION_KEY);
  if (existing) return existing;

  const id = window.crypto?.randomUUID
    ? window.crypto.randomUUID()
    : `session-${Date.now()}-${Math.random().toString(36).slice(2)}`;
  localStorage.setItem(SESSION_KEY, id);
  return id;
}

function getInviteRoomFromUrl() {
  return new URLSearchParams(window.location.search).get('room');
}

function formatTime(value) {
  const date = value ? new Date(value) : new Date();
  return new Intl.DateTimeFormat([], {
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}

async function apiRequest(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {}),
    },
    ...options,
  });

  if (!response.ok) {
    let message = 'Request failed';
    try {
      const body = await response.json();
      message = body.message || message;
    } catch {
      message = response.statusText || message;
    }
    throw new Error(message);
  }

  return response.json();
}

function createStompClient({ roomId, sessionId, onMessage, onStatus, onConnectionChange }) {
  let socket;
  let connected = false;
  let subscriptionId = 0;
  const pendingFrames = [];

  function sendFrame(command, headers = {}, body = '') {
    const headerLines = Object.entries(headers).map(([key, value]) => `${key}:${value}`);
    const frame = `${command}\n${headerLines.join('\n')}\n\n${body}\0`;
    if (!connected && command !== 'CONNECT') {
      pendingFrames.push(frame);
      return;
    }
    socket?.send(frame);
  }

  function subscribe(destination) {
    subscriptionId += 1;
    sendFrame('SUBSCRIBE', {
      id: `sub-${subscriptionId}`,
      destination,
    });
  }

  function handleFrame(rawFrame) {
    const [headerBlock, ...bodyParts] = rawFrame.split('\n\n');
    const lines = headerBlock.split('\n');
    const command = lines.shift();
    const headers = {};
    lines.forEach((line) => {
      const separatorIndex = line.indexOf(':');
      if (separatorIndex > -1) {
        headers[line.slice(0, separatorIndex)] = line.slice(separatorIndex + 1);
      }
    });

    const body = bodyParts.join('\n\n');
    if (command === 'CONNECTED') {
      connected = true;
      onConnectionChange(true);
      subscribe(`/topic/room/${roomId}`);
      subscribe(`/topic/room/${roomId}/status`);
      while (pendingFrames.length) socket.send(pendingFrames.shift());
      return;
    }

    if (command === 'MESSAGE') {
      const payload = JSON.parse(body || '{}');
      if (headers.destination?.endsWith('/status')) {
        onStatus(payload);
      } else {
        onMessage(payload);
      }
    }
  }

  socket = new WebSocket(`${API_BASE.replace('http', 'ws')}/ws-raw`);
  socket.onopen = () => {
    sendFrame('CONNECT', {
      'accept-version': '1.2',
      'heart-beat': '0,0',
      host: window.location.host,
      'client-session-id': sessionId,
    });
  };
  socket.onmessage = (event) => {
    event.data
      .split('\0')
      .filter(Boolean)
      .forEach(handleFrame);
  };
  socket.onclose = () => {
    connected = false;
    onConnectionChange(false);
  };
  socket.onerror = () => onConnectionChange(false);

  return {
    send(destination, body) {
      sendFrame('SEND', { destination }, JSON.stringify(body));
    },
    close() {
      if (connected) sendFrame('DISCONNECT');
      socket?.close();
    },
  };
}

function App() {
  const initialInviteRoom = useMemo(getInviteRoomFromUrl, []);
  const [sessionId] = useState(getSessionId);
  const [role, setRole] = useState(initialInviteRoom ? 'guest' : 'host');
  const [room, setRoom] = useState(null);
  const [name, setName] = useState(initialInviteRoom ? 'Guest' : 'Host');
  const [messages, setMessages] = useState([]);
  const [messageText, setMessageText] = useState('');
  const [copied, setCopied] = useState(false);
  const [connected, setConnected] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const [error, setError] = useState('');
  const clientRef = useRef(null);
  const messagesEndRef = useRef(null);

  const roomId = room?.roomId || initialInviteRoom || '';
  const roomStatus = room?.status || (initialInviteRoom ? 'INVITED' : 'EMPTY');
  const inviteLink = roomId
    ? `${window.location.origin}${window.location.pathname}?room=${roomId}`
    : '';
  const pendingGuest = room?.pendingGuestName || null;
  const activeGuest = room?.guestName || null;
  const canChat = room?.status === 'ACTIVE' && Boolean(connected);
  const isHost = role === 'host';
  const isActiveRoom = room?.status === 'ACTIVE';

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView?.({ behavior: 'smooth' });
  }, [messages, roomStatus]);

  useEffect(() => {
    if (!initialInviteRoom) return;

    apiRequest(`/api/rooms/${initialInviteRoom}?sessionId=${encodeURIComponent(sessionId)}`)
      .then((roomResponse) => {
        setRoom(roomResponse);
        addSystemMessage(`Invite opened for room ${roomResponse.roomId}.`);
      })
      .catch((requestError) => setError(requestError.message));
  }, [initialInviteRoom, sessionId]);

  useEffect(() => {
    if (!roomId) return undefined;

    clientRef.current?.close();
    const client = createStompClient({
      roomId,
      sessionId,
      onConnectionChange: setConnected,
      onStatus: handleStatusEvent,
      onMessage: handleChatMessage,
    });
    clientRef.current = client;

    return () => client.close();
    // The STOMP callbacks use stable state setters; reconnects are controlled by roomId/sessionId.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roomId, sessionId]);

  function addSystemMessage(text) {
    setMessages((currentMessages) => [
      ...currentMessages,
      {
        id: `system-${Date.now()}-${Math.random()}`,
        author: 'system',
        text,
        time: formatTime(),
      },
    ]);
  }

  function handleStatusEvent(event) {
    setRoom({
      roomId: event.roomId,
      hostName: event.hostName,
      guestName: event.guestName,
      pendingGuestName: event.pendingGuestName,
      status: event.status,
    });

    if (event.type === 'ROOM_DELETED' || event.type === 'ROOM_ENDED') {
      addSystemMessage(event.message || 'Room ended.');
      setConnected(false);
      return;
    }

    if (event.message) addSystemMessage(event.message);
    if (event.status === 'ACTIVE') setMenuOpen(false);
  }

  function handleChatMessage(event) {
    setMessages((currentMessages) => [
      ...currentMessages,
      {
        id: `message-${Date.now()}-${Math.random()}`,
        author: event.senderSessionId === sessionId ? 'host' : 'guest',
        text: event.text,
        time: formatTime(event.sentAt),
      },
    ]);
  }

  async function createRoom() {
    setError('');
    try {
      const nextRoom = await apiRequest('/api/rooms', {
        method: 'POST',
        body: JSON.stringify({ sessionId, name: name.trim() || 'Host' }),
      });
      setRole('host');
      setRoom(nextRoom);
      setMessages([]);
      addSystemMessage('Room created. Share the invite link with one friend.');
      window.history.replaceState(null, '', window.location.pathname);
    } catch (requestError) {
      setError(requestError.message);
    }
  }

  function requestToJoin(event) {
    event.preventDefault();
    setError('');
    if (!roomId) return;

    clientRef.current?.send('/app/join', {
      roomId,
      sessionId,
      name: name.trim() || 'Guest',
    });
    addSystemMessage('Join request sent. Waiting for host approval.');
  }

  function acceptGuest() {
    clientRef.current?.send('/app/accept', { roomId, sessionId });
  }

  function declineGuest() {
    clientRef.current?.send('/app/reject', { roomId, sessionId });
  }

  function endRoom() {
    clientRef.current?.send('/app/end', { roomId, sessionId });
  }

  function resetRoom() {
    clientRef.current?.close();
    setRoom(null);
    setMessages([]);
    setMessageText('');
    setError('');
    setConnected(false);
    setMenuOpen(false);
    setRole('host');
    window.history.replaceState(null, '', window.location.pathname);
  }

  async function copyInvite() {
    try {
      await navigator.clipboard.writeText(inviteLink);
      setCopied(true);
      setTimeout(() => setCopied(false), 1400);
    } catch {
      setError('Could not copy link from this browser.');
    }
  }

  function sendMessage(event, presetText) {
    event.preventDefault();
    const text = (presetText || messageText).trim();
    if (!canChat || !text) return;

    clientRef.current?.send('/app/send', {
      roomId,
      sessionId,
      text,
    });
    setMessageText('');
  }

  const headerTitle = isHost
    ? activeGuest || pendingGuest || 'Waiting room'
    : room?.hostName || `Room ${roomId}`;

  return (
    <main className={`app-shell ${isActiveRoom ? 'active-room' : ''} ${menuOpen ? 'menu-open' : ''}`}>
      {isActiveRoom && menuOpen && (
        <button
          aria-label="Close room menu"
          className="mobile-menu-scrim"
          onClick={() => setMenuOpen(false)}
          type="button"
        />
      )}

      <aside className="sidebar">
        <div className="brand-row">
          <div className="brand-mark">OC</div>
          <div>
            <h1>OpenComm</h1>
            <p>No accounts. One host. One guest.</p>
          </div>
        </div>

        <section className="room-panel">
          <p className="eyebrow">Live room</p>
          <h2>{roomId ? `Room ${roomId}` : 'Start a private room.'}</h2>
          <p className="muted">
            {isHost
              ? 'Create a room, share the invite, and approve one guest.'
              : 'Enter your name and request access from the host.'}
          </p>

          <label className="field-label" htmlFor="display-name">Display name</label>
          <input
            className="name-input"
            id="display-name"
            maxLength="40"
            onChange={(event) => setName(event.target.value)}
            value={name}
          />

          <div className={`status-pill ${statusClass(roomStatus)}`}>
            {statusText(roomStatus, pendingGuest, activeGuest)}
          </div>

          {error && <div className="error-box">{error}</div>}

          <div className="action-stack">
            {!roomId && (
              <button className="primary-action" onClick={createRoom}>
                Generate Invite Link
              </button>
            )}

            {roomId && (
              <div className="invite-box">
                <span>{inviteLink}</span>
                <button onClick={copyInvite}>{copied ? 'Copied' : 'Copy'}</button>
              </div>
            )}

            {!isHost && roomId && room?.status !== 'ACTIVE' && room?.status !== 'ENDED' && (
              <button className="primary-action" onClick={requestToJoin}>
                Request to Join
              </button>
            )}

            {isHost && room?.status === 'ACTIVE' && (
              <button className="ghost-action full" onClick={endRoom}>
                End Room
              </button>
            )}

            {roomId && (
              <button className="text-action" onClick={resetRoom}>
                Leave local view
              </button>
            )}
          </div>
        </section>

        <section className="flow-card">
          <p className="eyebrow">Room journey</p>
          <div className="flow-list">
            {flowSteps.map((step, index) => (
              <div
                className={`flow-step ${index <= currentStepIndex(roomStatus) ? 'complete' : ''}`}
                key={step.id}
              >
                <span>{index + 1}</span>
                <div>
                  <strong>{step.label}</strong>
                  <p>{step.detail}</p>
                </div>
              </div>
            ))}
          </div>
        </section>

        <section className="mobile-menu-details">
          <p className="eyebrow">Room details</p>
          <div className="avatar-row">
            <div className="avatar host">H</div>
            <div>
              <strong>{room?.hostName || (isHost ? name : 'Host')}</strong>
              <p>Host controls approvals and can end the room.</p>
            </div>
          </div>
          <div className="avatar-row">
            <div className={`avatar ${activeGuest ? 'guest' : ''}`}>G</div>
            <div>
              <strong>{activeGuest || pendingGuest || 'No guest yet'}</strong>
              <p>
                {activeGuest
                  ? 'Accepted guest can chat.'
                  : 'A guest must request access from the invite link.'}
              </p>
            </div>
          </div>
        </section>
      </aside>

      <section className="chat-window">
        <header className="chat-header">
          <div>
            <p className="eyebrow">{isHost ? 'Host view' : 'Guest view'}</p>
            <h2>{headerTitle}</h2>
          </div>
          <div className="chat-header-actions">
            {isActiveRoom && (
              <button
                aria-expanded={menuOpen}
                aria-label="Open room menu"
                className="mobile-menu-button"
                onClick={() => setMenuOpen(true)}
                type="button"
              >
                Menu
              </button>
            )}
            <div className={`connection-dot ${connected ? 'online' : 'idle'}`} />
          </div>
        </header>

        <div className="message-list">
          {!roomId && (
            <div className="empty-state">
              <h2>Start with the host.</h2>
              <p>Generate a room to get a real invite link backed by Spring Boot.</p>
            </div>
          )}

          {!isHost && roomId && room?.status !== 'ACTIVE' && (
            <WaitingPreview
              title={room?.status === 'PENDING' ? 'Request sent' : 'You are invited'}
              text={
                room?.status === 'PENDING'
                  ? 'The host sees your request. Chat unlocks only after approval.'
                  : 'Send a join request and wait for the host to accept.'
              }
              action={room?.status === 'PENDING' ? 'Waiting for Host' : 'Request to Join'}
              onAction={room?.status === 'PENDING' ? undefined : requestToJoin}
            />
          )}

          {messages.map((message) => (
            <div className={`message-bubble ${message.author}`} key={message.id}>
              <p>{message.text}</p>
              <span>{message.time}</span>
            </div>
          ))}

          {room?.status === 'ENDED' && (
            <div className="read-only-note">This chat is read-only because the room ended.</div>
          )}
          <div ref={messagesEndRef} />
        </div>

        {/* {canChat && (
          <div className="quick-replies">
            {quickReplies.map((reply) => (
              <button key={reply} onClick={(event) => sendMessage(event, reply)}>
                {reply}
              </button>
            ))}
          </div>
        )} */}

        <form className="composer" onSubmit={sendMessage}>
          <input
            aria-label="Message"
            disabled={!canChat}
            placeholder={composerPlaceholder(roomStatus, connected)}
            value={messageText}
            onChange={(event) => setMessageText(event.target.value)}
          />
          <button disabled={!canChat || !messageText.trim()} type="submit">
            Send
          </button>
        </form>

        {isHost && room?.status === 'PENDING' && (
          <div className="request-modal-backdrop" role="presentation">
            <section
              aria-labelledby="request-modal-title"
              aria-modal="true"
              className="request-modal"
              role="dialog"
            >
              <div className="request-avatar">{pendingGuest?.slice(0, 1) || 'G'}</div>
              <p className="eyebrow">Guest waiting</p>
              <h2 id="request-modal-title">{pendingGuest} wants to join</h2>
              <p>
                Approve this request to unlock the chat. This room can accept only
                one guest.
              </p>
              <div className="request-actions">
                <button className="primary-action compact" onClick={acceptGuest}>
                  Accept Guest
                </button>
                <button className="ghost-action" onClick={declineGuest}>
                  Decline
                </button>
              </div>
            </section>
          </div>
        )}
      </section>

      <aside className="detail-panel">
        <section className="profile-card">
          <p className="eyebrow">Room details</p>
          <div className="avatar-row">
            <div className="avatar host">H</div>
            <div>
              <strong>{room?.hostName || (isHost ? name : 'Host')}</strong>
              <p>Host controls approvals and can end the room.</p>
            </div>
          </div>
          <div className="avatar-row">
            <div className={`avatar ${activeGuest ? 'guest' : ''}`}>G</div>
            <div>
              <strong>{activeGuest || pendingGuest || 'No guest yet'}</strong>
              <p>
                {activeGuest
                  ? 'Accepted guest can chat.'
                  : 'A guest must request access from the invite link.'}
              </p>
            </div>
          </div>
        </section>

        <section className="rules-card">
          <p className="eyebrow">Live backend rules</p>
          <ul>
            <li>Room links do not require accounts.</li>
            <li>Host approval is required before chat starts.</li>
            <li>Only one accepted guest is allowed.</li>
            <li>Rooms live in memory and disappear when the backend restarts.</li>
          </ul>
        </section>
      </aside>
    </main>
  );
}

function WaitingPreview({ title, text, action, onAction }) {
  return (
    <div className="waiting-preview">
      <h2>{title}</h2>
      <p>{text}</p>
      <button disabled={!onAction} onClick={onAction}>{action}</button>
    </div>
  );
}

function currentStepIndex(status) {
  if (status === 'EMPTY' || status === 'INVITED') return status === 'INVITED' ? 1 : -1;
  if (status === 'WAITING') return 1;
  if (status === 'PENDING') return 2;
  if (status === 'ACTIVE' || status === 'ENDED') return 3;
  return -1;
}

function statusClass(status) {
  if (status === 'ACTIVE') return 'active';
  if (status === 'PENDING') return 'pending';
  if (status === 'ENDED') return 'ended';
  if (status === 'WAITING' || status === 'INVITED') return 'waiting';
  return 'not-created';
}

function statusText(status, pendingGuest, activeGuest) {
  if (status === 'EMPTY') return 'No room yet';
  if (status === 'INVITED') return 'Invite opened';
  if (status === 'WAITING') return 'Waiting for guest';
  if (status === 'PENDING') return `${pendingGuest || 'Guest'} is waiting`;
  if (status === 'ACTIVE') return `Connected with ${activeGuest || 'guest'}`;
  if (status === 'ENDED') return 'Room ended';
  return status;
}

function composerPlaceholder(status, connected) {
  if (status === 'ACTIVE' && connected) return 'Type a message';
  if (status === 'ACTIVE') return 'Connecting to chat';
  if (status === 'ENDED') return 'Room has ended';
  if (status === 'PENDING') return 'Waiting for host approval';
  return 'Chat unlocks after approval';
}

export default App;
