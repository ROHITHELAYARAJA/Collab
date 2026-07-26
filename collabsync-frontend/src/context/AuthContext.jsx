import { createContext, useContext, useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'

const AuthContext = createContext(null)

const API_BASE = '/api'

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [token, setToken] = useState(null)
  const [refreshToken, setRefreshToken] = useState(null)
  const [loading, setLoading] = useState(true)
  const navigate = useNavigate()

  // Initialize from localStorage
  useEffect(() => {
    const storedToken = localStorage.getItem('collabsync_token')
    const storedRefreshToken = localStorage.getItem('collabsync_refresh_token')
    const storedUser = localStorage.getItem('collabsync_user')

    if (storedToken) {
      setToken(storedToken)
      setRefreshToken(storedRefreshToken)
      if (storedUser) {
        try {
          setUser(JSON.parse(storedUser))
        } catch (e) {
          console.error('Failed to parse stored user', e)
        }
      }
    }
    setLoading(false)
  }, [])

  const apiCall = useCallback(async (endpoint, options = {}) => {
    const headers = {
      'Content-Type': 'application/json',
      ...(options.headers || {}),
    }

    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }

    const response = await fetch(`${API_BASE}${endpoint}`, {
      ...options,
      headers,
    })

    // Handle 401 - token expired
    if (response.status === 401 && token && refreshToken) {
      const refreshed = await refreshAccessToken()
      if (refreshed) {
        // Retry the original request
        headers['Authorization'] = `Bearer ${token}`
        return fetch(`${API_BASE}${endpoint}`, {
          ...options,
          headers,
        })
      } else {
        // Refresh failed, logout
        logout()
        navigate('/login')
        return response
      }
    }

    return response
  }, [token, refreshToken, navigate])

  const refreshAccessToken = async () => {
    try {
      const response = await fetch(`${API_BASE}/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken })
      })

      if (response.ok) {
        const data = await response.json()
        setToken(data.accessToken)
        setRefreshToken(data.refreshToken)
        localStorage.setItem('collabsync_token', data.accessToken)
        localStorage.setItem('collabsync_refresh_token', data.refreshToken)
        return true
      }
    } catch (err) {
      console.error('Token refresh failed:', err)
    }
    return false
  }

  const login = async (email, password) => {
    let response
    try {
      response = await fetch(`${API_BASE}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password })
      })
    } catch (err) {
      throw new Error('Cannot reach the server. Please make sure the backend is running.')
    }

    let data = {}
    try {
      data = await response.json()
    } catch (err) {
      // Handle empty body or non-JSON response safely
    }

    if (!response.ok) {
      throw new Error(data.message || `Login failed (${response.status})`)
    }

    setToken(data.accessToken)
    setRefreshToken(data.refreshToken)
    setUser({
      userId: data.userId,
      email: data.email,
      displayName: data.displayName
    })
    localStorage.setItem('collabsync_token', data.accessToken)
    localStorage.setItem('collabsync_refresh_token', data.refreshToken)
    localStorage.setItem('collabsync_user', JSON.stringify({
      userId: data.userId,
      email: data.email,
      displayName: data.displayName
    }))
    return data
  }

  const register = async (email, password, displayName) => {
    let response
    try {
      response = await fetch(`${API_BASE}/auth/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password, displayName })
      })
    } catch (err) {
      throw new Error('Cannot reach the server. Please make sure the backend is running.')
    }

    let data = {}
    try {
      data = await response.json()
    } catch (err) {
      // Handle empty body safely
    }

    if (!response.ok) {
      throw new Error(data.message || `Registration failed (${response.status})`)
    }

    // Auto-login after registration
    return login(email, password)
  }

  const logout = () => {
    setUser(null)
    setToken(null)
    setRefreshToken(null)
    localStorage.removeItem('collabsync_token')
    localStorage.removeItem('collabsync_refresh_token')
    localStorage.removeItem('collabsync_user')
    navigate('/login')
  }

  const value = {
    user,
    token,
    refreshToken,
    loading,
    login,
    register,
    logout,
    apiCall,
    isAuthenticated: !!token && !!user
  }

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}