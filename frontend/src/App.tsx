import type { FormEvent } from 'react'
import { useEffect, useState } from 'react'
import { loadProjects, loadSession, loginUser, logoutUser, registerUser, validateInvite } from './api'
import { ProjectCard } from './components/ProjectCard'
import type {
  InviteValidationResponse,
  LoginPayload,
  Project,
  RegisterPayload,
  SessionResponse,
  SessionUser,
} from './types'

type AppApi = {
  loadProjects: () => Promise<Project[]>
  loadSession: () => Promise<SessionResponse>
  validateInvite: (token: string) => Promise<InviteValidationResponse>
  registerUser: (payload: RegisterPayload) => Promise<SessionResponse>
  loginUser: (payload: LoginPayload) => Promise<SessionResponse>
  logoutUser: () => Promise<void>
}

type AppProps = {
  api?: Partial<AppApi>
}

type RouteState =
  | { name: 'feed' }
  | { name: 'login' }
  | { name: 'register'; inviteToken: string }

const defaultApi: AppApi = {
  loadProjects,
  loadSession,
  validateInvite,
  registerUser,
  loginUser,
  logoutUser,
}

export default function App({ api }: AppProps) {
  const apiClient = { ...defaultApi, ...api }
  const fetchProjects = apiClient.loadProjects
  const fetchSession = apiClient.loadSession
  const checkInvite = apiClient.validateInvite
  const register = apiClient.registerUser
  const login = apiClient.loginUser
  const logout = apiClient.logoutUser

  const [route, setRoute] = useState<RouteState>(() => routeFromLocation(window.location))
  const [session, setSession] = useState<SessionResponse>({ authenticated: false })
  const [sessionLoading, setSessionLoading] = useState(true)
  const [projects, setProjects] = useState<Project[]>([])
  const [projectsLoading, setProjectsLoading] = useState(true)
  const [projectsError, setProjectsError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    fetchSession()
      .then((nextSession) => {
        if (!cancelled) {
          setSession(nextSession)
          setSessionLoading(false)
        }
      })
      .catch((nextError: Error) => {
        if (!cancelled) {
          setSessionLoading(false)
          console.error(nextError)
        }
      })

    return () => {
      cancelled = true
    }
  }, [fetchSession])

  useEffect(() => {
    const handlePopState = () => setRoute(routeFromLocation(window.location))
    window.addEventListener('popstate', handlePopState)
    return () => window.removeEventListener('popstate', handlePopState)
  }, [])

  useEffect(() => {
    let cancelled = false

    fetchProjects()
      .then((nextProjects) => {
        if (!cancelled) {
          setProjects(nextProjects)
          setProjectsLoading(false)
        }
      })
      .catch((nextError: Error) => {
        if (!cancelled) {
          setProjectsError(nextError.message)
          setProjectsLoading(false)
        }
      })

    return () => {
      cancelled = true
    }
  }, [fetchProjects])

  useEffect(() => {
    if (!sessionLoading && session.authenticated && route.name !== 'feed') {
      navigateTo('/', setRoute)
    }
  }, [route.name, session, sessionLoading])

  function handleAuthenticated(nextSession: SessionResponse) {
    setSession(nextSession)
    navigateTo('/', setRoute)
  }

  async function handleLogout() {
    await logout()
    setSession({ authenticated: false })
    navigateTo('/', setRoute)
  }

  return (
    <main className="page-shell">
      <header className="page-header">
        <button className="brand-lockup" type="button" onClick={() => navigateTo('/', setRoute)}>
          <span className="hero__eyebrow">Mitbauen Project Feed</span>
          <strong>Mitbauen Native</strong>
        </button>

        <div className="page-header__actions">
          {sessionLoading ? <span className="page-header__status">Checking session...</span> : null}
          {!sessionLoading && session.authenticated && session.user ? (
            <>
              <span className="page-header__status">Signed in as {session.user.displayName}</span>
              <button className="ghost-button" type="button" onClick={() => void handleLogout()}>
                Logout
              </button>
            </>
          ) : null}
          {!sessionLoading && !session.authenticated ? (
            <button className="ghost-button" type="button" onClick={() => navigateTo('/login', setRoute)}>
              Sign in
            </button>
          ) : null}
        </div>
      </header>

      {route.name === 'feed' ? (
        <FeedView
          projects={projects}
          loading={projectsLoading}
          error={projectsError}
          session={session}
          onNavigate={setRoute}
        />
      ) : null}

      {route.name === 'login' ? (
        <LoginView
          onAuthenticate={handleAuthenticated}
          onLogin={login}
          onNavigate={setRoute}
        />
      ) : null}

      {route.name === 'register' ? (
        <RegisterView
          inviteToken={route.inviteToken}
          onAuthenticate={handleAuthenticated}
          onNavigate={setRoute}
          onRegister={register}
          onValidateInvite={checkInvite}
        />
      ) : null}
    </main>
  )
}

type FeedViewProps = {
  projects: Project[]
  loading: boolean
  error: string | null
  session: SessionResponse
  onNavigate: (route: RouteState) => void
}

function FeedView({ projects, loading, error, session, onNavigate }: FeedViewProps) {
  const invitePath = session.user?.inviteToken ? `/register?invite=${session.user.inviteToken}` : null

  return (
    <>
      <section className="hero">
        <p className="hero__eyebrow">Mitbauen Project Feed</p>
        <h1>Projects that already have founder energy behind them.</h1>
        <p className="hero__copy">
          The first slice is intentionally public and read-only: people can browse real projects, see founder
          commitment, and understand where help is needed.
        </p>
      </section>

      <section className="auth-banner">
        {session.authenticated && session.user ? (
          <>
            <div>
              <strong>Your invite link is ready.</strong>
              <p>Reuse it with as many new registrations as you want.</p>
            </div>
            {invitePath ? <code>{invitePath}</code> : null}
          </>
        ) : (
          <>
            <div>
              <strong>Invite-only sign-in is the next slice.</strong>
              <p>Use your invite link to register, or sign in if you already have an account.</p>
            </div>
            <button className="primary-button" type="button" onClick={() => navigateTo('/login', onNavigate)}>
              Open sign in
            </button>
          </>
        )}
      </section>

      {loading ? <p className="state-card">Loading projects...</p> : null}
      {error ? <p className="state-card state-card--error">{error}</p> : null}

      {!loading && !error ? (
        <section className="feed-grid" aria-label="Project feed">
          {projects.map((project) => (
            <ProjectCard key={project.id} project={project} />
          ))}
        </section>
      ) : null}
    </>
  )
}

type LoginViewProps = {
  onAuthenticate: (session: SessionResponse) => void
  onLogin: (payload: LoginPayload) => Promise<SessionResponse>
  onNavigate: (route: RouteState) => void
}

function LoginView({ onAuthenticate, onLogin, onNavigate }: LoginViewProps) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      const nextSession = await onLogin({ email, password })
      onAuthenticate(nextSession)
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : 'Unable to sign in.')
      setSubmitting(false)
    }
  }

  return (
    <section className="auth-shell">
      <article className="auth-card">
        <p className="hero__eyebrow">Sign in</p>
        <h1>Pick up where you left off.</h1>
        <p className="auth-card__copy">
          The backend owns the session cookie. This page only asks for the credentials we need to create it.
        </p>

        <form className="auth-form" onSubmit={handleSubmit}>
          <label>
            Email
            <input value={email} onChange={(event) => setEmail(event.target.value)} type="email" required />
          </label>

          <label>
            Password
            <input value={password} onChange={(event) => setPassword(event.target.value)} type="password" required />
          </label>

          {error ? <p className="auth-error">{error}</p> : null}

          <button className="primary-button" type="submit" disabled={submitting}>
            {submitting ? 'Signing in...' : 'Sign in'}
          </button>
        </form>

        <button className="ghost-button auth-card__secondary" type="button" onClick={() => navigateTo('/', onNavigate)}>
          Back to project feed
        </button>
      </article>
    </section>
  )
}

type RegisterViewProps = {
  inviteToken: string
  onAuthenticate: (session: SessionResponse) => void
  onNavigate: (route: RouteState) => void
  onRegister: (payload: RegisterPayload) => Promise<SessionResponse>
  onValidateInvite: (token: string) => Promise<InviteValidationResponse>
}

function RegisterView({ inviteToken, onAuthenticate, onNavigate, onRegister, onValidateInvite }: RegisterViewProps) {
  const [validation, setValidation] = useState<InviteValidationResponse | null>(null)
  const [validating, setValidating] = useState(true)
  const [email, setEmail] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    if (!inviteToken) {
      setValidation({ valid: false })
      setValidating(false)
      return
    }

    onValidateInvite(inviteToken)
      .then((nextValidation) => {
        if (!cancelled) {
          setValidation(nextValidation)
          if (nextValidation.allowedEmail) {
            setEmail(nextValidation.allowedEmail)
          }
          setValidating(false)
        }
      })
      .catch((nextError: Error) => {
        if (!cancelled) {
          setError(nextError.message)
          setValidating(false)
        }
      })

    return () => {
      cancelled = true
    }
  }, [inviteToken, onValidateInvite])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      const nextSession = await onRegister({ inviteToken, email, displayName, password })
      onAuthenticate(nextSession)
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : 'Unable to register.')
      setSubmitting(false)
    }
  }

  return (
    <section className="auth-shell">
      <article className="auth-card">
        <p className="hero__eyebrow">Register</p>
        <h1>Claim your invite-only account.</h1>
        <p className="auth-card__copy">
          The bootstrap invite is validated first, then we create your password hash and secure session in the backend.
        </p>

        {validating ? <p className="state-card">Checking invite link...</p> : null}

        {!validating && validation?.valid ? (
          <form className="auth-form" onSubmit={handleSubmit}>
            <label>
              Email
              <input
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                type="email"
                required
                readOnly={Boolean(validation.allowedEmail)}
              />
            </label>

            <label>
              Display name
              <input
                value={displayName}
                onChange={(event) => setDisplayName(event.target.value)}
                type="text"
                minLength={2}
                required
              />
            </label>

            <label>
              Password
              <input
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                type="password"
                minLength={8}
                pattern="(?=.*\\d)(?=.*[a-z])(?=.*[A-Z]).{8,}"
                title="Use at least 8 characters with an uppercase letter, a lowercase letter, and a digit."
                required
              />
            </label>

            <p className="auth-note">Use at least 8 characters with an uppercase letter, a lowercase letter, and a digit.</p>

            {validation.allowedEmail ? (
              <p className="auth-note">This invite is restricted to {validation.allowedEmail}.</p>
            ) : null}
            {error ? <p className="auth-error">{error}</p> : null}

            <button className="primary-button" type="submit" disabled={submitting}>
              {submitting ? 'Creating account...' : 'Create account'}
            </button>
          </form>
        ) : null}

        {!validating && validation && !validation.valid ? (
          <div className="state-card state-card--error">
            This invite link is missing or invalid. Ask the person who invited you for a fresh link.
          </div>
        ) : null}

        <button className="ghost-button auth-card__secondary" type="button" onClick={() => navigateTo('/login', onNavigate)}>
          I already have an account
        </button>
      </article>
    </section>
  )
}

function navigateTo(path: string, onNavigate: (route: RouteState) => void) {
  window.history.pushState({}, '', path)
  onNavigate(routeFromLocation(window.location))
}

function routeFromLocation(location: Location): RouteState {
  if (location.pathname === '/login') {
    return { name: 'login' }
  }
  if (location.pathname === '/register') {
    return { name: 'register', inviteToken: new URLSearchParams(location.search).get('invite') ?? '' }
  }
  return { name: 'feed' }
}
