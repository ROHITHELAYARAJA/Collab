import { useState, useEffect } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export function WorkspaceListPage() {
  const { apiCall } = useAuth()
  const navigate = useNavigate()
  const [workspaces, setWorkspaces] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [newWorkspaceName, setNewWorkspaceName] = useState('')
  const [creating, setCreating] = useState(false)

  useEffect(() => {
    loadWorkspaces()
  }, [])

  const loadWorkspaces = async () => {
    setLoading(true)
    try {
      const response = await apiCall('/workspaces')
      if (response.ok) {
        // Use the wrapper's json() function
        const data = await response.json()
        setWorkspaces(data.content || data)
      } else {
        setError('Failed to load workspaces')
      }
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  const handleCreate = async (e) => {
    e.preventDefault()
    if (!newWorkspaceName.trim() || creating) return

    setCreating(true)
    try {
      const response = await apiCall('/workspaces', {
        method: 'POST',
        body: JSON.stringify({ name: newWorkspaceName.trim() })
      })

      if (response.ok) {
        // Workspace creation might return no content on success, handle gracefully
        try {
            const data = await response.json()
            setWorkspaces(prev => [...prev, data])
            setShowCreateModal(false)
            setNewWorkspaceName('')
        } catch (e) {
            // If no content, just close modal
            setShowCreateModal(false)
            setNewWorkspaceName('')
        }
      } else {
        const data = await response.json()
        alert(data.message || 'Failed to create workspace')
      }
    } catch (err) {
      alert(err.message)
    } finally {
      setCreating(false)
    }
  }

  const handleDelete = async (workspaceId) => {
    if (!confirm('Are you sure you want to delete this workspace? This cannot be undone.')) return

    try {
      const response = await apiCall(`/workspaces/${workspaceId}`, { method: 'DELETE' })
      if (response.ok) {
        setWorkspaces(prev => prev.filter(w => w.id !== workspaceId))
      } else {
        alert('Failed to delete workspace')
      }
    } catch (err) {
      alert(err.message)
    }
  }

  if (loading) {
    return <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '100vh' }}>Loading workspaces...</div>
  }

  return (
    <div className="page-container">
      <div className="page-header">
        <h1 className="page-title">Workspaces</h1>
        <p className="page-subtitle">Your collaborative spaces</p>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
        <button className="btn btn-primary" onClick={() => setShowCreateModal(true)}>
          + New Workspace
        </button>
      </div>

      {workspaces.length === 0 ? (
        <div className="card">
          <div className="empty-state">
            <div className="empty-state-icon">📁</div>
            <h3 style={{ marginBottom: '0.5rem' }}>No workspaces yet</h3>
            <p style={{ color: 'var(--text-secondary)' }}>Create your first workspace to start collaborating</p>
            <button className="btn btn-primary" style={{ marginTop: '1rem' }} onClick={() => setShowCreateModal(true)}>
              Create Workspace
            </button>
          </div>
        </div>
      ) : (
        <div className="workspace-grid">
          {workspaces.map(workspace => (
            <div key={workspace.id} className="card workspace-card">
              <div className="workspace-card-header">
                <Link to={`/workspaces/${workspace.id}`} className="workspace-name" style={{ textDecoration: 'none', color: 'inherit', flex: 1 }}>
                  {workspace.name}
                </Link>
                <button
                  className="btn btn-ghost btn-danger"
                  onClick={() => handleDelete(workspace.id)}
                  style={{ padding: '0.25rem 0.5rem', fontSize: '0.75rem' }}
                >
                  Delete
                </button>
              </div>
              <div className="workspace-meta">
                <p>Owner: {workspace.ownerId === (JSON.parse(localStorage.getItem('collabsync_user') || '{}')).userId ? 'You' : workspace.ownerId}</p>
                <p>Created: {new Date(workspace.createdAt).toLocaleDateString()}</p>
              </div>
              <div className="workspace-footer">
                <Link to={`/workspaces/${workspace.id}`} className="btn btn-primary" style={{ flex: 1, textAlign: 'center' }}>
                  Open
                </Link>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Create Workspace Modal */}
      {showCreateModal && (
        <div className="modal-overlay" onClick={() => setShowCreateModal(false)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <div className="modal-header">
              <h2 className="modal-title">Create Workspace</h2>
              <button className="modal-close" onClick={() => setShowCreateModal(false)}>✕</button>
            </div>
            <form id="create-workspace-form" onSubmit={handleCreate} className="modal-body">
              <div className="form-group">
                <label className="form-label" htmlFor="workspaceName">Workspace Name</label>
                <input
                  type="text"
                  id="workspaceName"
                  className="form-input"
                  value={newWorkspaceName}
                  onChange={(e) => setNewWorkspaceName(e.target.value)}
                  placeholder="My Project"
                  required
                  maxLength={100}
                  autoFocus
                />
              </div>
            </form>
            <div className="modal-footer">
              <button type="button" className="btn btn-secondary" onClick={() => setShowCreateModal(false)}>Cancel</button>
              <button type="submit" form="create-workspace-form" className="btn btn-primary" disabled={creating || !newWorkspaceName.trim()}>
                {creating ? 'Creating...' : 'Create Workspace'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}