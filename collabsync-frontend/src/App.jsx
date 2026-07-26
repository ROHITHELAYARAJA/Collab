import { Routes, Route, Navigate, Link } from 'react-router-dom'
import { useAuth } from './context/AuthContext'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { WorkspaceListPage } from './pages/WorkspaceListPage'
import { DocumentListPage } from './pages/DocumentListPage'
import { EditorPage } from './pages/EditorPage'
import { ConnectionBanner } from './components/ConnectionBanner'

function ProtectedRoute({ children }) {
  const { isAuthenticated, loading } = useAuth()

  if (loading) {
    return <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '100vh' }}>Loading...</div>
  }

  return isAuthenticated ? children : <Navigate to="/login" replace />
}

function PublicRoute({ children }) {
  const { isAuthenticated, loading } = useAuth()

  if (loading) {
    return <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '100vh' }}>Loading...</div>
  }

  return isAuthenticated ? <Navigate to="/workspaces" replace /> : children
}

function App() {
  return (
    <div className="app-layout">
      <header className="main-header">
        <div className="header-brand">CollabSync</div>
        <nav className="header-actions" style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
          <ProtectedRoute>
            <Link to="/workspaces" className="btn btn-secondary" style={{ textDecoration: 'none' }}>
              Workspaces
            </Link>
          </ProtectedRoute>
        </nav>
      </header>

      <Routes>
        <Route
          path="/login"
          element={
            <PublicRoute>
              <LoginPage />
            </PublicRoute>
          }
        />
        <Route
          path="/register"
          element={
            <PublicRoute>
              <RegisterPage />
            </PublicRoute>
          }
        />
        <Route
          path="/workspaces"
          element={
            <ProtectedRoute>
              <WorkspaceListPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/workspaces/:workspaceId"
          element={
            <ProtectedRoute>
              <DocumentListPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/documents/:documentId"
          element={
            <ProtectedRoute>
              <EditorPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="*"
          element={<Navigate to="/workspaces" replace />}
        />
      </Routes>

      <ConnectionBanner />
    </div>
  )
}

export default App