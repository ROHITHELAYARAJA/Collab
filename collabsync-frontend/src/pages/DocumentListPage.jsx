import { useState, useEffect } from 'react'
import { Link, useParams, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export function DocumentListPage() {
  const { apiCall } = useAuth()
  const { workspaceId } = useParams()
  const navigate = useNavigate()
  const [documents, setDocuments] = useState([])
  const [workspace, setWorkspace] = useState(null)
  const [loading, setLoading] = useState(true)
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [newDocTitle, setNewDocTitle] = useState('')
  const [creating, setCreating] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    loadData()
  }, [workspaceId])

  const loadData = async () => {
    try {
      setLoading(true)
      const [wsRes, docsRes] = await Promise.all([
        apiCall(`/workspaces/${workspaceId}`),
        apiCall(`/documents?workspaceId=${workspaceId}`)
      ])

      if (wsRes.ok) setWorkspace(await wsRes.json())
      if (docsRes.ok) {
        const data = await docsRes.json()
        setDocuments(data.content || data)
      }
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  const handleCreate = async (e) => {
    e.preventDefault()
    if (!newDocTitle.trim() || creating) return

    setCreating(true)
    try {
      const response = await apiCall('/documents', {
        method: 'POST',
        body: JSON.stringify({ workspaceId, title: newDocTitle.trim() })
      })

      if (response.ok) {
        const doc = await response.json()
        setDocuments(prev => [doc, ...prev])
        setShowCreateModal(false)
        setNewDocTitle('')
      } else {
        const data = await response.json()
        alert(data.message || 'Failed to create document')
      }
    } catch (err) {
      alert(err.message)
    } finally {
      setCreating(false)
    }
  }

  const handleDelete = async (docId) => {
    if (!confirm('Delete this document? This cannot be undone.')) return

    try {
      const response = await apiCall(`/documents/${docId}`, { method: 'DELETE' })
      if (response.ok) {
        setDocuments(prev => prev.filter(d => d.id !== docId))
      } else {
        alert('Failed to delete document')
      }
    } catch (err) {
      alert(err.message)
    }
  }

  if (loading) {
    return <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '100vh' }}>Loading...</div>
  }

  return (
    <div className="page-container">
      <div className="page-header">
        <Link to="/workspaces" className="btn btn-ghost" style={{ marginBottom: '0.5rem', display: 'inline-block' }}>
          ← Back to Workspaces
        </Link>
        <h1 className="page-title">{workspace?.name || 'Documents'}</h1>
        <p className="page-subtitle">Collaborative documents in this workspace</p>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
        <button className="btn btn-primary" onClick={() => setShowCreateModal(true)}>
          + New Document
        </button>
      </div>

      {documents.length === 0 ? (
        <div className="card">
          <div className="empty-state">
            <div className="empty-state-icon">📄</div>
            <h3 style={{ marginBottom: '0.5rem' }}>No documents yet</h3>
            <p style={{ color: 'var(--text-secondary)' }}>Create your first document to start collaborating</p>
            <button className="btn btn-primary" style={{ marginTop: '1rem' }} onClick={() => setShowCreateModal(true)}>
              Create Document
            </button>
          </div>
        </div>
      ) : (
        <div className="document-list">
          {documents.map(doc => (
            <div key={doc.id} className="document-item">
              <div className="document-info">
                <Link to={`/documents/${doc.id}`} style={{ textDecoration: 'none', color: 'inherit' }}>
                  <span className="document-title">{doc.title}</span>
                </Link>
                <span className="document-meta">
                  Updated {new Date(doc.updatedAt).toLocaleString()} · v{doc.version || 1}
                </span>
              </div>
              <div className="document-actions">
                <Link to={`/documents/${doc.id}`} className="btn btn-primary" style={{ fontSize: '0.75rem' }}>Open</Link>
                <button className="btn btn-secondary btn-danger" style={{ fontSize: '0.75rem' }} onClick={() => handleDelete(doc.id)}>
                  Delete
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Create Document Modal */}
      {showCreateModal && (
        <div className="modal-overlay" onClick={() => setShowCreateModal(false)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <div className="modal-header">
              <h2 className="modal-title">Create Document</h2>
              <button className="modal-close" onClick={() => setShowCreateModal(false)}>✕</button>
            </div>
            <form onSubmit={handleCreate} className="modal-body">
              <div className="form-group">
                <label className="form-label" htmlFor="docTitle">Document Title</label>
                <input
                  type="text"
                  id="docTitle"
                  className="form-input"
                  value={newDocTitle}
                  onChange={(e) => setNewDocTitle(e.target.value)}
                  placeholder="Meeting Notes"
                  required
                  maxLength={255}
                  autoFocus
                />
              </div>
              {error && <div className="alert alert-error" style={{ marginBottom: '1rem' }}>{error}</div>}
            </form>
            <div className="modal-footer">
              <button type="button" className="btn btn-secondary" onClick={() => setShowCreateModal(false)}>Cancel</button>
              <button type="submit" className="btn btn-primary" disabled={creating || !newDocTitle.trim()}>
                {creating ? 'Creating...' : 'Create Document'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}