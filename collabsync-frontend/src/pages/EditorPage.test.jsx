import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import { EditorPage } from '../pages/EditorPage'
import { AuthProvider } from '../context/AuthContext'
import { SocketProvider } from '../context/SocketContext'

// Mock WebSocket
class MockWebSocket {
  constructor(url) {
    this.url = url
    this.readyState = WebSocket.CONNECTING
    this.onopen = null
    this.onmessage = null
    this.onclose = null
    this.onerror = null
    this.sentFrames = []

    // Simulate connection
    setTimeout(() => {
      this.readyState = WebSocket.OPEN
      if (this.onopen) this.onopen({})
    }, 0)
  }

  send(frame) {
    this.sentFrames.push(frame)
  }

  close() {
    this.readyState = WebSocket.CLOSED
    if (this.onclose) this.onclose({ code: 1000, reason: 'Normal closure' })
  }

  // Helper to simulate receiving a STOMP frame
  simulateMessage(data) {
    if (this.onmessage) this.onmessage({ data })
  }

  // Helper to simulate STOMP CONNECTED
  simulateConnected() {
    this.simulateMessage('CONNECTED\nversion:1.1\nheart-beat:10000,10000\n\n\0')
  }
}

// Mock window.location
Object.defineProperty(window, 'location', {
  value: {
    protocol: 'http:',
    host: 'localhost:3000'
  },
  writable: true
})

const { mockUseParams, mockUseNavigate, mockApiCall, mockUser, mockSocketState } = vi.hoisted(() => ({
  mockUseParams: vi.fn(),
  mockUseNavigate: vi.fn(),
  mockApiCall: vi.fn(),
  mockUser: { userId: 'user1', displayName: 'Test User' },
  mockSocketState: { connectionStatus: 'connected', connected: true }
}))

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    useParams: () => mockUseParams(),
    useNavigate: () => mockUseNavigate(),
    Link: ({ children, to }) => <a href={to}>{children}</a>
  }
})

vi.mock('../context/AuthContext', () => ({
  AuthProvider: ({ children }) => (
    <div data-testid="auth-provider">
      {children}
    </div>
  ),
  useAuth: () => ({
    user: mockUser,
    apiCall: mockApiCall,
    isAuthenticated: true,
    logout: vi.fn()
  })
}))

// Mock socket context
const mockSubscribe = vi.fn().mockReturnValue(() => {})
const mockSend = vi.fn().mockResolvedValue(undefined)
vi.mock('../context/SocketContext', () => ({
  SocketProvider: ({ children }) => <div data-testid="socket-provider">{children}</div>,
  useSocket: () => ({
    ...mockSocketState,
    subscribe: mockSubscribe,
    send: mockSend
  })
}))

describe('EditorPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockUseParams.mockReturnValue({ documentId: 'doc-123' })
    mockUseNavigate.mockReturnValue(vi.fn())
    Object.assign(mockSocketState, { connectionStatus: 'connected', connected: true })
    mockSubscribe.mockReset().mockReturnValue(() => {})
    mockSend.mockReset().mockResolvedValue(undefined)

    mockApiCall.mockResolvedValue({
      ok: true,
      json: async () => ({
        id: 'doc-123',
        title: 'Test Document',
        content: 'Initial content',
        workspaceId: 'ws-123'
      })
    })
  })

  afterEach(() => {
    vi.resetModules()
  })

  it('loads document and displays content', async () => {
    render(
      <BrowserRouter>
        <AuthProvider>
          <SocketProvider>
            <EditorPage />
          </SocketProvider>
        </AuthProvider>
      </BrowserRouter>
    )

    await waitFor(() => {
      expect(screen.getAllByText('Test Document')[0]).toBeInTheDocument()
    })

    await waitFor(() => {
      expect(screen.getByDisplayValue('Initial content')).toBeInTheDocument()
    })
  })

  it('shows connection status as connected', async () => {
    render(
      <BrowserRouter>
        <AuthProvider>
          <SocketProvider>
            <EditorPage />
          </SocketProvider>
        </AuthProvider>
      </BrowserRouter>
    )

    await waitFor(() => {
      expect(screen.getByText('connected')).toBeInTheDocument()
    })
  })

  it('shows pending operations count', async () => {
    render(
      <BrowserRouter>
        <AuthProvider>
          <SocketProvider>
            <EditorPage />
          </SocketProvider>
        </AuthProvider>
      </BrowserRouter>
    )

    await waitFor(() => {
      expect(screen.getAllByText('Test Document')[0]).toBeInTheDocument()
    })

    // Initially no pending ops
    expect(screen.queryByText(/pending/)).not.toBeInTheDocument()
  })

  it('displays presence bar with current user', async () => {
    render(
      <BrowserRouter>
        <AuthProvider>
          <SocketProvider>
            <EditorPage />
          </SocketProvider>
        </AuthProvider>
      </BrowserRouter>
    )

    await waitFor(() => {
      expect(screen.getByText('Collaborators (1)')).toBeInTheDocument()
    })

    await waitFor(() => {
      expect(screen.getByText('Test User (you)')).toBeInTheDocument()
    })
  })

  it('shows chat panel', async () => {
    render(
      <BrowserRouter>
        <AuthProvider>
          <SocketProvider>
            <EditorPage />
          </SocketProvider>
        </AuthProvider>
      </BrowserRouter>
    )

    await waitFor(() => {
      expect(screen.getByText('Chat')).toBeInTheDocument()
    })

    expect(screen.getByPlaceholderText('Type a message...')).toBeInTheDocument()
  })

  it('sends chat message on form submit', async () => {
    render(
      <BrowserRouter>
        <AuthProvider>
          <SocketProvider>
            <EditorPage />
          </SocketProvider>
        </AuthProvider>
      </BrowserRouter>
    )

    await waitFor(() => {
      expect(screen.getByPlaceholderText('Type a message...')).toBeInTheDocument()
    })

    fireEvent.change(screen.getByPlaceholderText('Type a message...'), {
      target: { value: 'Hello World' }
    })

    fireEvent.click(screen.getByText('Send'))

    await waitFor(() => {
      expect(mockSend).toHaveBeenCalledWith(
        '/app/chat/doc-123/send',
        expect.objectContaining({
          type: 'CHAT_MESSAGE',
          content: 'Hello World'
        })
      )
    })
  })

  it('sends typing indicator when typing in chat', async () => {
    render(
      <BrowserRouter>
        <AuthProvider>
          <SocketProvider>
            <EditorPage />
          </SocketProvider>
        </AuthProvider>
      </BrowserRouter>
    )

    await waitFor(() => {
      expect(screen.getByPlaceholderText('Type a message...')).toBeInTheDocument()
    })

    fireEvent.change(screen.getByPlaceholderText('Type a message...'), {
      target: { value: 'He' }
    })

    await waitFor(() => {
      expect(mockSend).toHaveBeenCalledWith(
        '/app/chat/doc-123/typing',
        expect.objectContaining({
          type: 'TYPING_INDICATOR',
          typing: true
        })
      )
    })
  })

  it('displays remote cursor when presence message received', async () => {
    // Set up mock to return additional presence
    mockSubscribe.mockImplementation((destination, callback) => {
      if (destination === '/topic/doc/doc-123/presence') {
        // Simulate remote user joining
        setTimeout(() => {
          callback({
            type: 'PRESENCE_JOIN',
            senderId: 'user2',
            displayName: 'Remote User',
            timestamp: new Date().toISOString()
          })
        }, 0)
      }
      return () => {}
    })

    render(
      <BrowserRouter>
        <AuthProvider>
          <SocketProvider>
            <EditorPage />
          </SocketProvider>
        </AuthProvider>
      </BrowserRouter>
    )

    await waitFor(() => {
      expect(screen.getByText('Collaborators (2)')).toBeInTheDocument()
    })

    await waitFor(() => {
      expect(screen.getByText('Remote User')).toBeInTheDocument()
    })
  })

  it('applies remote DOC_OP to content', async () => {
    mockSubscribe.mockImplementation((destination, callback) => {
      if (destination === '/topic/doc/doc-123') {
        // Simulate remote insert
        setTimeout(() => {
          callback({
            type: 'DOC_OP',
            senderId: 'user2',
            payload: { opType: 'INSERT', position: 0, content: 'Remote: ' },
            serverSeq: 2
          })
        }, 0)
      }
      return () => {}
    })

    render(
      <BrowserRouter>
        <AuthProvider>
          <SocketProvider>
            <EditorPage />
          </SocketProvider>
        </AuthProvider>
      </BrowserRouter>
    )

    await waitFor(() => {
      expect(screen.getByDisplayValue('Initial content')).toBeInTheDocument()
    })

    await waitFor(() => {
      expect(screen.getByDisplayValue('Remote: Initial content')).toBeInTheDocument()
    })
  })

  it('does not apply own echoed operations', async () => {
    mockSubscribe.mockImplementation((destination, callback) => {
      if (destination === '/topic/doc/doc-123') {
        // Simulate own operation echoed back
        setTimeout(() => {
          callback({
            type: 'DOC_OP',
            senderId: 'user1', // Same as current user
            payload: { opType: 'INSERT', position: 0, content: 'Echo: ' },
            serverSeq: 2
          })
        }, 0)
      }
      return () => {}
    })

    render(
      <BrowserRouter>
        <AuthProvider>
          <SocketProvider>
            <EditorPage />
          </SocketProvider>
        </AuthProvider>
      </BrowserRouter>
    )

    await waitFor(() => {
      expect(screen.getByDisplayValue('Initial content')).toBeInTheDocument()
    })

    // Content should not change since it's our own echoed operation
    await waitFor(() => {
      expect(screen.getByDisplayValue('Initial content')).toBeInTheDocument()
    })
  })

  it('shows reconnecting status when connection lost', async () => {
    Object.assign(mockSocketState, { connectionStatus: 'connecting', connected: false })

    render(
      <BrowserRouter>
        <AuthProvider>
          <SocketProvider>
            <EditorPage />
          </SocketProvider>
        </AuthProvider>
      </BrowserRouter>
    )

    await waitFor(() => {
      expect(screen.getByText('connecting')).toBeInTheDocument()
    })

    expect(screen.getByText('Reconnecting...')).toBeInTheDocument()
  })
})
