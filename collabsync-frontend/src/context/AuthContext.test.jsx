import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BrowserRouter } from 'react-router-dom'
import { AuthProvider, useAuth } from '../context/AuthContext'
import { LoginPage } from '../pages/LoginPage'
import { RegisterPage } from '../pages/RegisterPage'

// Mock fetch globally
global.fetch = vi.fn()

// Test wrapper with providers
const renderWithProviders = (ui) => {
  return render(
    <BrowserRouter>
      <AuthProvider>
        {ui}
      </AuthProvider>
    </BrowserRouter>
  )
}

describe('AuthContext', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  afterEach(() => {
    vi.resetAllMocks()
  })

  it('provides login function', async () => {
    const mockResponse = {
      ok: true,
      json: vi.fn().mockResolvedValue({
        accessToken: 'test-access-token',
        refreshToken: 'test-refresh-token',
        userId: 'user-123',
        email: 'test@example.com',
        displayName: 'Test User'
      })
    }
    global.fetch.mockResolvedValue(mockResponse)

    let loginFn = null
    const TestComponent = () => {
      const { login } = useAuth()
      loginFn = login
      return <div>Test</div>
    }

    renderWithProviders(<TestComponent />)

    const result = await loginFn('test@example.com', 'password123')

    expect(result).toEqual({
      accessToken: 'test-access-token',
      refreshToken: 'test-refresh-token',
      userId: 'user-123',
      email: 'test@example.com',
      displayName: 'Test User'
    })

    expect(localStorage.getItem('collabsync_token')).toBe('test-access-token')
    expect(localStorage.getItem('collabsync_refresh_token')).toBe('test-refresh-token')
  })

  it('throws error on login failure', async () => {
    const mockResponse = {
      ok: false,
      json: vi.fn().mockResolvedValue({ message: 'Invalid credentials' })
    }
    global.fetch.mockResolvedValue(mockResponse)

    let loginFn = null
    const TestComponent = () => {
      const { login } = useAuth()
      loginFn = login
      return <div>Test</div>
    }

    renderWithProviders(<TestComponent />)

    await expect(loginFn('test@example.com', 'wrong')).rejects.toThrow('Invalid credentials')
  })

  it('provides register function', async () => {
    const mockResponse = {
      ok: true,
      json: vi.fn().mockResolvedValue({
        accessToken: 'test-access-token',
        refreshToken: 'test-refresh-token',
        userId: 'user-123',
        email: 'new@example.com',
        displayName: 'New User'
      })
    }
    global.fetch.mockResolvedValue(mockResponse)

    let registerFn = null
    const TestComponent = () => {
      const { register } = useAuth()
      registerFn = register
      return <div>Test</div>
    }

    renderWithProviders(<TestComponent />)

    const result = await registerFn('new@example.com', 'password123', 'New User')

    expect(result).toEqual({
      accessToken: 'test-access-token',
      refreshToken: 'test-refresh-token',
      userId: 'user-123',
      email: 'new@example.com',
      displayName: 'New User'
    })
  })

  it('provides logout function', async () => {
    localStorage.setItem('collabsync_token', 'test-token')
    localStorage.setItem('collabsync_refresh_token', 'test-refresh')
    localStorage.setItem('collabsync_user', JSON.stringify({ userId: '123' }))

    let logoutFn = null
    const TestComponent = () => {
      const { logout } = useAuth()
      logoutFn = logout
      return <div>Test</div>
    }

    renderWithProviders(<TestComponent />)

    logoutFn()

    expect(localStorage.getItem('collabsync_token')).toBeNull()
    expect(localStorage.getItem('collabsync_refresh_token')).toBeNull()
    expect(localStorage.getItem('collabsync_user')).toBeNull()
  })

  it('provides apiCall function', async () => {
    const mockResponse = {
      ok: true,
      json: vi.fn().mockResolvedValue({ data: 'test' })
    }
    global.fetch.mockResolvedValue(mockResponse)

    localStorage.setItem('collabsync_token', 'test-token')

    let apiCallFn = null
    const TestComponent = () => {
      const { apiCall } = useAuth()
      apiCallFn = apiCall
      return <div>Test</div>
    }

    renderWithProviders(<TestComponent />)

    const response = await apiCallFn('/test')
    expect(response.ok).toBe(true)
    expect(global.fetch).toHaveBeenCalledWith('/api/test', expect.objectContaining({
      headers: expect.objectContaining({
        'Authorization': 'Bearer test-token',
        'Content-Type': 'application/json'
      })
    }))
  })
})

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders login form', () => {
    renderWithProviders(<LoginPage />)

    expect(screen.getByText('Welcome back')).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toBeInTheDocument()
    expect(screen.getByLabelText('Password')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('shows error on failed login', async () => {
    const mockResponse = {
      ok: false,
      json: vi.fn().mockResolvedValue({ message: 'Invalid credentials' })
    }
    global.fetch.mockResolvedValue(mockResponse)

    renderWithProviders(<LoginPage />)

    await userEvent.type(screen.getByLabelText('Email'), 'test@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'wrongpassword')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    await waitFor(() => {
      expect(screen.getByText('Invalid credentials')).toBeInTheDocument()
    })
  })

  it('navigates to register page on link click', () => {
    renderWithProviders(<LoginPage />)

    const link = screen.getByRole('link', { name: /Create one/i })
    expect(link).toHaveAttribute('href', '/register')
  })
})

describe('RegisterPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders registration form', () => {
    renderWithProviders(<RegisterPage />)

    expect(screen.getByRole('heading', { name: 'Create account' })).toBeInTheDocument()
    expect(screen.getByLabelText('Display Name')).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toBeInTheDocument()
    expect(screen.getByLabelText('Password')).toBeInTheDocument()
    expect(screen.getByLabelText('Confirm Password')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Create account' })).toBeInTheDocument()
  })

  it('shows error when passwords do not match', async () => {
    renderWithProviders(<RegisterPage />)

    await userEvent.type(screen.getByLabelText('Display Name'), 'Test User')
    await userEvent.type(screen.getByLabelText('Email'), 'test@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'password123')
    await userEvent.type(screen.getByLabelText('Confirm Password'), 'different')
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }))

    await waitFor(() => {
      expect(screen.getByText('Passwords do not match')).toBeInTheDocument()
    })
  })

  it('shows error when password is too short', async () => {
    renderWithProviders(<RegisterPage />)

    await userEvent.type(screen.getByLabelText('Display Name'), 'Test User')
    await userEvent.type(screen.getByLabelText('Email'), 'test@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'short')
    await userEvent.type(screen.getByLabelText('Confirm Password'), 'short')
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }))

    await waitFor(() => {
      expect(screen.getByText('Password must be at least 8 characters')).toBeInTheDocument()
    })
  })

  it('navigates to login page on link click', () => {
    renderWithProviders(<RegisterPage />)

    const link = screen.getByRole('link', { name: /Sign in/i })
    expect(link).toHaveAttribute('href', '/login')
  })
})