import { useSocket } from '../context/SocketContext'

export function ConnectionBanner() {
  const { connectionStatus } = useSocket()

  if (connectionStatus === 'connected') return null

  const messages = {
    connecting: 'Reconnecting...',
    disconnected: 'Disconnected. Retrying...'
  }

  return (
    <div className={`connection-banner ${connectionStatus}`} role="status" aria-live="polite">
      <span style={{ width: '8px', height: '8px', borderRadius: '50%',
        background: connectionStatus === 'connecting' ? '#f59e0b' : '#ef4444',
        animation: connectionStatus === 'connecting' ? 'pulse 1s infinite' : 'none'
      }} />
      <span>{messages[connectionStatus] || 'Connecting...'}</span>
    </div>
  )
}