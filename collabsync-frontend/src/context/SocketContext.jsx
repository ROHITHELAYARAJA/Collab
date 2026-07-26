import { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react'
import { useAuth } from './AuthContext'

const SocketContext = createContext(null)

export function SocketProvider({ children }) {
  const { token, isAuthenticated } = useAuth()
  const [socket, setSocket] = useState(null)
  const [connectionStatus, setConnectionStatus] = useState('disconnected')
  const [stompConnected, setStompConnected] = useState(false)
  const [subscriptions, setSubscriptions] = useState(new Map())
  const reconnectTimeoutRef = useRef(null)
  const reconnectAttemptsRef = useRef(0)
  const messageIdRef = useRef(0)
  const pendingAcksRef = useRef(new Map())
  const stompConnectedRef = useRef(false)

  // Send a STOMP frame
  const sendFrame = useCallback((command, headers = {}, body = '') => {
    if (!socket || socket.readyState !== WebSocket.OPEN) return false

    let frame = `${command}\n`
    Object.entries(headers).forEach(([key, value]) => {
      frame += `${key}:${value}\n`
    })
    frame += `\n${body}`

    try {
      socket.send(frame)
      return true
    } catch (error) {
      console.error('Failed to send frame:', error)
      return false
    }
  }, [socket])

  // Connect to WebSocket
  const connect = useCallback(() => {
    if (!isAuthenticated || !token) return

    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current)
    }

    setConnectionStatus('connecting')

    const wsUrl = `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/api/ws?accessToken=${encodeURIComponent(token)}`
    const ws = new WebSocket(wsUrl)

    ws.onopen = () => {
      console.log('WebSocket connected')
      setSocket(ws)
      reconnectAttemptsRef.current = 0
      ws.send(`CONNECT
Authorization:Bearer ${token}
accept-version:1.1,1.0
heart-beat:10000,10000

\0`)
    }

    ws.onmessage = (event) => {
      handleStompMessage(event.data)
    }

    ws.onclose = (event) => {
      console.log('WebSocket closed:', event.code, event.reason)
      setSocket(null)
      setConnectionStatus('disconnected')
      stompConnectedRef.current = false
      setStompConnected(false)

      if (isAuthenticated && token) {
        scheduleReconnect()
      }
    }

    ws.onerror = (error) => {
      console.error('WebSocket error:', error)
    }
  }, [isAuthenticated, token, sendFrame])

  const scheduleReconnect = useCallback(() => {
    if (reconnectAttemptsRef.current >= 10) {
      console.log('Max reconnect attempts reached')
      return
    }

    const delay = Math.min(1000 * Math.pow(2, reconnectAttemptsRef.current), 30000) + Math.random() * 1000
    reconnectAttemptsRef.current++

    reconnectTimeoutRef.current = setTimeout(() => {
      connect()
    }, delay)
  }, [connect])

  // Parse STOMP frames
  const handleStompMessage = useCallback((data) => {
    const frames = data.split('\0').filter(f => f.trim())

    frames.forEach(frame => {
      const lines = frame.split('\n')
      const command = lines[0]
      const headers = {}
      let bodyStart = 1

      while (bodyStart < lines.length && lines[bodyStart].trim() !== '') {
        const colonIndex = lines[bodyStart].indexOf(':')
        if (colonIndex > 0) {
          const key = lines[bodyStart].substring(0, colonIndex)
          const value = lines[bodyStart].substring(colonIndex + 1)
          headers[key] = value
        }
        bodyStart++
      }

      const body = lines.slice(bodyStart + 1).join('\n')

      switch (command) {
        case 'CONNECTED':
          stompConnectedRef.current = true
          setStompConnected(true)
          setConnectionStatus('connected')
          console.log('STOMP connected:', headers)
          // Re-subscribe to previous subscriptions
          subscriptions.forEach((callbacks, destination) => {
            sendFrame('SUBSCRIBE', {
              destination,
              id: `sub-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
              ack: 'auto',
            })
          })
          break

        case 'MESSAGE':
          const destination = headers.destination
          try {
            const message = JSON.parse(body)
            const callbacks = subscriptions.get(destination)
            if (callbacks) {
              callbacks.forEach(cb => cb(message, headers))
            }
          } catch (error) {
            console.error('Failed to parse message:', error)
          }
          break

        case 'ERROR':
          console.error('STOMP error:', headers.message, body)
          break

        case 'RECEIPT':
          const receiptId = headers['receipt-id']
          const ackCallback = pendingAcksRef.current.get(receiptId)
          if (ackCallback) {
            ackCallback()
            pendingAcksRef.current.delete(receiptId)
          }
          break
      }
    })
  }, [subscriptions, sendFrame])

  // Subscribe to a destination
  const subscribe = useCallback((destination, callback) => {
    setSubscriptions(prev => {
      const newMap = new Map(prev)
      const callbacks = newMap.get(destination) || new Set()
      callbacks.add(callback)
      newMap.set(destination, callbacks)

      // If already connected, send SUBSCRIBE frame
      if (stompConnectedRef.current && socket?.readyState === WebSocket.OPEN) {
        sendFrame('SUBSCRIBE', {
          destination,
          id: `sub-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
          ack: 'auto',
        })
      }

      return newMap
    })

    // Return unsubscribe function
    return () => {
      setSubscriptions(prev => {
        const newMap = new Map(prev)
        const callbacks = newMap.get(destination)
        if (callbacks) {
          callbacks.delete(callback)
          if (callbacks.size === 0) {
            newMap.delete(destination)
            if (stompConnectedRef.current && socket?.readyState === WebSocket.OPEN) {
              sendFrame('UNSUBSCRIBE', { destination })
            }
          }
        }
        return newMap
      })
    }
  }, [socket, sendFrame])

  // Send message to a destination
  const send = useCallback((destination, body, headers = {}) => {
    if (!stompConnectedRef.current || socket?.readyState !== WebSocket.OPEN) {
      return Promise.reject(new Error('Not connected'))
    }

    const messageId = `msg-${++messageIdRef.current}`

    return new Promise((resolve, reject) => {
      const receiptId = `receipt-${messageId}`

      // Store the resolve/reject callbacks
      pendingAcksRef.current.set(receiptId, () => resolve())

      // Set timeout for ack
      setTimeout(() => {
        if (pendingAcksRef.current.has(receiptId)) {
          pendingAcksRef.current.delete(receiptId)
          reject(new Error('Message send timeout'))
        }
      }, 10000)

      sendFrame('SEND', {
        destination,
        'content-type': 'application/json',
        'receipt': receiptId,
        ...headers,
      }, JSON.stringify(body))
    })
  }, [socket, sendFrame])

  // Disconnect
  const disconnect = useCallback(() => {
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current)
    }
    if (socket) {
      sendFrame('DISCONNECT')
      socket.close()
    }
    setSocket(null)
    setConnectionStatus('disconnected')
    stompConnectedRef.current = false
    setStompConnected(false)
  }, [socket, sendFrame])

  // Auto-connect when authenticated
  useEffect(() => {
    if (isAuthenticated && token) {
      connect()
    } else {
      disconnect()
    }

    return () => {
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current)
      }
    }
  }, [isAuthenticated, token, connect, disconnect])

  const value = {
    socket,
    connectionStatus,
    connect,
    disconnect,
    subscribe,
    send,
    stompConnected,
    connected: stompConnected,
  }

  return (
    <SocketContext.Provider value={value}>
      {children}
    </SocketContext.Provider>
  )
}

export function useSocket() {
  const context = useContext(SocketContext)
  if (!context) {
    throw new Error('useSocket must be used within a SocketProvider')
  }
  return context
}


