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
          setProjectsError('We could not load the projects right now. Please try again in a moment.')
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
          <img className="brand-lockup__mark" src="/mitbauen-mark.svg" alt="" />
          <span className="brand-lockup__text">
            <span className="hero__eyebrow">Mitbauen Projects</span>
            <strong>Mitbauen Native</strong>
          </span>
        </button>

        <div className="page-header__actions">
          {sessionLoading ? <span className="page-header__status">Loading...</span> : null}
          {!sessionLoading && session.authenticated && session.user ? (
            <>
              <span className="page-header__status">Welcome, {session.user.displayName}</span>
              <button className="ghost-button" type="button" onClick={() => void handleLogout()}>
                Log out
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
}

function FeedView({ projects, loading, error }: FeedViewProps) {
  return (
    <>
      <section className="hero-grid">
        <div className="hero">
          <p className="hero__eyebrow">Mitbauen Projects</p>
          <h1>Discover real projects looking for the right people.</h1>
          <p className="hero__copy">
            Explore early-stage ideas, founder-led projects, and opportunities where your skills can make a real
            difference.
          </p>
        </div>

        <div className="hero-stack">
          <article className="hero-panel hero-panel--dark">
            <p className="hero-panel__eyebrow">For Builders</p>
            <h2>Find projects with momentum.</h2>
            <p>
              Browse projects where founders have already shared what they are building, what they need, and how others
              can get involved.
            </p>
          </article>

          <article className="hero-panel">
            <p className="hero-panel__eyebrow">Public Snapshot</p>
            <p className="hero-panel__copy">
              Start with the public feed: scan the founder role, energy level, and open roles before deciding where you
              want to lean in.
            </p>
          </article>
        </div>
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
      setError('We could not sign you in. Please check your email and password.')
      setSubmitting(false)
    }
  }

  return (
    <section className="auth-shell">
      <article className="auth-card">
        <p className="hero__eyebrow">Sign in</p>
        <h1>Welcome back.</h1>
        <p className="auth-card__copy">
          Sign in to continue exploring projects and managing your invite access.
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
          Back to projects
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
          setValidating(false)
        }
      })
      .catch(() => {
        if (!cancelled) {
          setError('We could not check this invite link. Please try again.')
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
      setError('We could not create your account. Please check your details and try again.')
      setSubmitting(false)
    }
  }

  return (
    <section className="auth-shell">
      <article className="auth-card">
        <p className="hero__eyebrow">Create account</p>
        <h1>Join Mitbauen.</h1>
        <p className="auth-card__copy">
          Create your account with your invite link and start discovering projects you can help build.
        </p>

        {validating ? <p className="state-card">Checking your invite...</p> : null}

        {!validating && validation?.valid ? (
          <form className="auth-form" onSubmit={handleSubmit}>
            <label>
              Email
              <input
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                type="email"
                required
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
                required
              />
            </label>

            <p className="auth-note">Use at least 8 characters, including a number and both uppercase and lowercase letters.</p>

            {error ? <p className="auth-error">{error}</p> : null}

            <button className="primary-button" type="submit" disabled={submitting}>
              {submitting ? 'Creating account...' : 'Create account'}
            </button>
          </form>
        ) : null}

        {!validating && validation && !validation.valid ? (
          <div className="state-card state-card--error">
            This invite link does not work anymore. Please ask for a new invite link.
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
