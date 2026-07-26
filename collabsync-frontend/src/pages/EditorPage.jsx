import { useState, useEffect, useRef, useCallback } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useSocket } from '../context/SocketContext'
import { ConnectionBanner } from '../components/ConnectionBanner'
import {
  applyOperation,
  transformOperation,
  transformPendingOps,
  adjustCursorPosition,
  diff,
  getCursorCoordinates,
  getUserColor
} from '../utils/ot'

export function EditorPage() {
  const { documentId } = useParams()
  const { apiCall, user } = useAuth()
  const { connectionStatus, subscribe, send, connected } = useSocket()
  const navigate = useNavigate()

  const [document, setDocument] = useState(null)
  const [content, setContent] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [serverSeq, setServerSeq] = useState(0)
  const [pendingOps, setPendingOps] = useState([])
  const clientSeqRef = useRef(0)
  const [myCursorPos, setMyCursorPos] = useState({ position: 0, selectionEnd: 0 })
  const [presence, setPresence] = useState({})
  const [cursors, setCursors] = useState({})
  const [messages, setMessages] = useState([])
  const [newMessage, setNewMessage] = useState('')
  const [typingUsers, setTypingUsers] = useState(new Set())

  const textareaRef = useRef(null)
  const messagesEndRef = useRef(null)
  const isComposingRef = useRef(false)

  // Load document on mount
  useEffect(() => {
    loadDocument()
  }, [documentId])

  const loadDocument = async () => {
    try {
      const response = await apiCall(`/documents/${documentId}`)
      if (response.ok) {
        const doc = await response.json()
        setDocument(doc)
        setContent(doc.content || '')
      } else {
        setError('Document not found')
      }
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  // WebSocket subscriptions
  useEffect(() => {
    if (!connected || !documentId) return

    const unsubOp = subscribe(`/topic/doc/${documentId}`, (msg) => {
      if (msg.type === 'DOC_OP') {
        handleRemoteOp(msg)
      }
    })

    const unsubPresence = subscribe(`/topic/doc/${documentId}/presence`, (msg) => {
      if (msg.type === 'PRESENCE_JOIN') {
        setPresence(prev => ({ ...prev, [msg.senderId]: { displayName: msg.displayName } }))
      } else if (msg.type === 'PRESENCE_LEAVE') {
        setPresence(prev => {
          const n = { ...prev }
          delete n[msg.senderId]
          return n
        })
      } else if (msg.type === 'CURSOR_UPDATE') {
        setCursors(prev => ({ ...prev, [msg.senderId]: { position: msg.cursorPosition, selectionEnd: msg.selectionEnd } }))
      }
    })

    const unsubAck = subscribe(`/user/queue/acks`, (msg) => {
      if (msg.type === 'ACK' && msg.success) {
        setPendingOps(prev => prev.filter(op => op.clientSeq !== msg.clientSeq))
        setServerSeq(msg.serverSeq)
      }
    })

    // Join document
    send('/app/doc/' + documentId + '/join', { documentId })

    return () => {
      unsubOp()
      unsubPresence()
      unsubAck()
      send('/app/doc/' + documentId + '/leave', { documentId })
    }
  }, [connected, documentId, subscribe, send])

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  useEffect(() => {
    if (!user?.userId) return

    setPresence(prev => ({
      ...prev,
      [user.userId]: { displayName: user.displayName }
    }))
  }, [user?.userId, user?.displayName])


  // Handle remote operations
  const handleRemoteOp = useCallback((msg) => {
    if (msg.senderId === user?.userId) return // Skip own echoed operations

    const { payload, serverSeq: seq } = msg

    setContent(prevContent => {
      // Transform remote operation against pending local operations
      let transformedPayload = payload
      for (const pending of pendingOps) {
        if (pending.clientSeq > seq) {
          transformedPayload = transformOperation(transformedPayload, pending.payload)
        }
      }

      // Also transform pending local operations against the remote operation
      setPendingOps(prev => transformPendingOps(prev, transformedPayload))

      return applyOperation(prevContent, transformedPayload)
    })

    setServerSeq(seq)
  }, [user, pendingOps])

  // Send local operation
  const sendOperation = useCallback(async (opPayload) => {
    if (!documentId) return

    const myClientSeq = ++clientSeqRef.current

    // Add to pending
    const pendingOp = { clientSeq: myClientSeq, payload: opPayload }
    setPendingOps(prev => [...prev, pendingOp])

    // Send to server
    await send('/app/doc/' + documentId + '/edit', {
      type: 'DOC_OP',
      documentId,
      payload: opPayload,
      clientSeq: myClientSeq
    })
  }, [documentId, send])

  // Handle local text changes
  const handleChange = useCallback((e) => {
    if (isComposingRef.current) return

    const newContent = e.target.value
    const cursorPos = e.target.selectionStart
    const selectionEnd = e.target.selectionEnd

    // Calculate operation from diff
    const oldContent = content
    setContent(newContent)
    setMyCursorPos({ position: cursorPos, selectionEnd })

    const op = diff(oldContent, newContent, cursorPos)
    if (op) {
      sendOperation(op)
    }
  }, [content, sendOperation])

  const handleSelect = useCallback((e) => {
    setMyCursorPos({ position: e.target.selectionStart, selectionEnd: e.target.selectionEnd })
  }, [])

  const handleCompositionStart = () => { isComposingRef.current = true }
  const handleCompositionEnd = (e) => {
    isComposingRef.current = false
    handleChange(e)
  }

  // Send cursor updates
  useEffect(() => {
    if (!connected || !documentId) return

    const debounced = setTimeout(() => {
      send('/app/doc/' + documentId + '/cursor', {
        type: 'CURSOR_UPDATE',
        documentId,
        cursorPosition: myCursorPos.position,
        selectionEnd: myCursorPos.selectionEnd
      })
    }, 50)

    return () => clearTimeout(debounced)
  }, [myCursorPos, documentId, connected, send])

  // Chat subscriptions
  useEffect(() => {
    if (!connected || !documentId) return

    const unsubMsg = subscribe(`/topic/chat/${documentId}`, (msg) => {
      if (msg.type === 'CHAT_MESSAGE') {
        setMessages(prev => [...prev, msg])
      }
    })

    const unsubTyping = subscribe(`/topic/chat/${documentId}/typing`, (msg) => {
      if (msg.type === 'TYPING_INDICATOR' && msg.senderId !== user?.userId) {
        if (msg.typing) {
          setTypingUsers(prev => new Set([...prev, msg.senderId]))
        } else {
          setTypingUsers(prev => {
            const n = new Set(prev)
            n.delete(msg.senderId)
            return n
          })
        }
      }
    })

    return () => {
      unsubMsg()
      unsubTyping()
    }
  }, [connected, documentId, subscribe, user])

  const handleSendMessage = async (e) => {
    e.preventDefault()
    if (!newMessage.trim()) return

    await send('/app/chat/' + documentId + '/send', {
      type: 'CHAT_MESSAGE',
      roomId: documentId,
      content: newMessage.trim()
    })
    setNewMessage('')
  }

  const handleTyping = useCallback(() => {
    send('/app/chat/' + documentId + '/typing', { type: 'TYPING_INDICATOR', roomId: documentId, typing: true })

    clearTimeout(typingTimeoutRef.current)
    typingTimeoutRef.current = setTimeout(() => {
      send('/app/chat/' + documentId + '/typing', { type: 'TYPING_INDICATOR', roomId: documentId, typing: false })
    }, 1000)
  }, [documentId, send])

  const typingTimeoutRef = useRef(null)

  // Calculate cursor coordinates for remote cursors
  const getCursorCoords = useCallback((position) => {
    if (!textareaRef.current) return { x: 0, y: 0 }
    const text = content.substring(0, position)
    const lines = text.split('\n')
    const lineHeight = 22
    const charWidth = 8.4
    return {
      x: (lines[lines.length - 1].length * charWidth) + 16,
      y: ((lines.length - 1) * lineHeight) + 16
    }
  }, [content])

  if (loading) {
    return <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '100vh' }}>Loading document...</div>
  }

  if (error) {
    return <div className="alert alert-error" style={{ padding: '2rem', textAlign: 'center' }}>{error}</div>
  }

  if (!document) return null


  return (
    <div className="app-layout">
      <ConnectionBanner />
      <header className="main-header">
        <div className="header-brand">CollabSync</div>
        <div className="header-actions">
          <Link to={`/workspaces/${document.workspaceId}`} className="btn btn-ghost" style={{ textDecoration: 'none' }}>
            ← Workspace
          </Link>
          <span style={{ fontSize: '0.875rem', color: 'var(--text-secondary)', marginRight: '1rem' }}>
            {document.title}
          </span>
          <div className="user-menu">
            <div className="user-avatar">{user?.displayName?.[0]?.toUpperCase()}</div>
            <span style={{ fontSize: '0.875rem' }}>{user?.displayName}</span>
          </div>
        </div>
      </header>

      <div className="editor-layout">
        <div className="editor-main">
          <div className="editor-toolbar">
            <h2 className="editor-title">{document.title}</h2>
            <div className="editor-status">
              <span className={`status-dot ${connectionStatus}`}></span>
              <span>{connectionStatus}</span>
              {pendingOps.length > 0 && (
                <span style={{ marginLeft: '0.5rem', color: 'var(--accent-warning)' }}>● {pendingOps.length} pending</span>
              )}
            </div>
          </div>

          <div style={{ flex: 1, position: 'relative', overflow: 'hidden' }}>
            <textarea
              ref={textareaRef}
              className="editor-textarea"
              value={content}
              onChange={handleChange}
              onSelect={handleSelect}
              onCompositionStart={handleCompositionStart}
              onCompositionEnd={handleCompositionEnd}
              placeholder="Start typing to collaborate..."
              spellCheck={false}
            />

            {/* Remote cursors */}
            {Object.entries(cursors).map(([id, cursor]) => {
              const userPresence = presence[id]
              if (!userPresence) return null
              const coords = getCursorCoords(cursor.position)
              return (
                <div
                  key={id}
                  className="remote-cursor"
                  style={{
                    left: coords.x,
                    top: coords.y,
                    color: getUserColor(id)
                  }}
                >
                  <div className="remote-cursor-caret"></div>
                  <div className="remote-cursor-label">{userPresence.displayName}</div>
                </div>
              )
            })}
          </div>
        </div>

        <aside className="editor-sidebar" style={{ width: '320px', minWidth: '300px' }}>
          {/* Presence */}
          <div className="presence-bar">
            <div className="presence-title">Collaborators ({Object.keys(presence).length})</div>
            <div className="presence-list">
              {Object.entries(presence).map(([id, p]) => (
                <span
                  key={id}
                  className={`presence-badge ${id === user?.userId ? 'current-user' : ''}`}
                  style={{
                    backgroundColor: id === user?.userId ? 'var(--accent-primary)' : 'var(--bg-tertiary)',
                    color: id === user?.userId ? 'white' : 'var(--text-secondary)'
                  }}
                >
                  <span className="dot" style={{ backgroundColor: id === user?.userId ? 'white' : 'var(--accent-success)' }}></span>
                  {p.displayName} {id === user?.userId ? '(you)' : ''}
                </span>
              ))}
            </div>
          </div>

          {/* Chat Panel */}
          <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0 }}>
            <div className="chat-header" style={{ padding: '1rem', borderBottom: '1px solid var(--border-color)' }}>
              <h3 style={{ margin: 0, fontSize: '1rem' }}>Chat</h3>
            </div>

            <div className="chat-messages" style={{ flex: 1, overflowY: 'auto', padding: '1rem', display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              {messages.map((msg, idx) => (
                <div key={`${msg.timestamp}-${idx}`} className={`message ${msg.senderId === user?.userId ? 'own' : ''}`}>
                  <div className="message-header">
                    <span className="message-author">{msg.senderId === user?.userId ? 'You' : msg.senderId}</span>
                    <span className="message-time">{new Date(msg.timestamp).toLocaleTimeString()}</span>
                  </div>
                  <div className="message-content">{msg.content}</div>
                </div>
              ))}
              <div ref={messagesEndRef} />
            </div>

            {typingUsers.size > 0 && (
              <div className="typing-indicator">
                {Array.from(typingUsers).join(', ')} {typingUsers.size === 1 ? 'is' : 'are'} typing...
              </div>
            )}

            <form onSubmit={handleSendMessage} className="chat-input-form">
              <input
                type="text"
                className="chat-input"
                value={newMessage}
                onChange={(e) => { setNewMessage(e.target.value); handleTyping() }}
                placeholder="Type a message..."
              />
              <button type="submit" className="btn btn-primary" disabled={!newMessage.trim()}>Send</button>
            </form>
          </div>
        </aside>
      </div>
    </div>
  )
}



