import type { FormEvent } from 'react'
import { useEffect, useState } from 'react'
import {
  createProject,
  deleteProject,
  loadProfile,
  loadProject,
  loadProjects,
  loadSession,
  loginUser,
  logoutUser,
  registerUser,
  updateProfile,
  updateProject,
  validateInvite,
} from './api'
import { ProjectCard } from './components/ProjectCard'
import { ProjectDetailView } from './components/ProjectDetailView'
import { ProjectFormView } from './components/ProjectFormView'
import { ProfileView } from './components/ProfileView'
import type {
  InviteValidationResponse,
  LoginPayload,
  Project,
  ProjectDetails,
  ProjectMutationResponse,
  ProjectPayload,
  RegisterPayload,
  SessionResponse,
  UserProfile,
  UserProfilePayload,
} from './types'

type FeedNotice = 'created' | 'updated' | 'deleted' | null
type DetailNotice = 'created' | 'updated' | null

type AppApi = {
  loadProjects: () => Promise<Project[]>
  loadProject: (slug: string) => Promise<ProjectDetails>
  loadProfile: () => Promise<UserProfile>
  loadSession: () => Promise<SessionResponse>
  validateInvite: (token: string) => Promise<InviteValidationResponse>
  registerUser: (payload: RegisterPayload) => Promise<SessionResponse>
  loginUser: (payload: LoginPayload) => Promise<SessionResponse>
  logoutUser: () => Promise<void>
  updateProfile: (payload: UserProfilePayload) => Promise<UserProfile>
  createProject: (payload: ProjectPayload) => Promise<ProjectMutationResponse>
  updateProject: (slug: string, payload: ProjectPayload) => Promise<ProjectMutationResponse>
  deleteProject: (slug: string) => Promise<void>
}

type AppProps = {
  api?: Partial<AppApi>
}

type RouteState =
  | { name: 'feed'; highlightSlug: string | null; notice: FeedNotice }
  | { name: 'login' }
  | { name: 'register'; inviteToken: string }
  | { name: 'profile' }
  | { name: 'projectCreate' }
  | { name: 'projectDetail'; slug: string; notice: DetailNotice }
  | { name: 'projectEdit'; slug: string }

const defaultApi: AppApi = {
  loadProjects,
  loadProject,
  loadProfile,
  loadSession,
  validateInvite,
  registerUser,
  loginUser,
  logoutUser,
  updateProfile,
  createProject,
  updateProject,
  deleteProject,
}

export default function App({ api }: AppProps) {
  const apiClient = { ...defaultApi, ...api }
  const fetchProjects = apiClient.loadProjects
  const fetchProject = apiClient.loadProject
  const fetchProfile = apiClient.loadProfile
  const fetchSession = apiClient.loadSession
  const checkInvite = apiClient.validateInvite
  const register = apiClient.registerUser
  const login = apiClient.loginUser
  const logout = apiClient.logoutUser
  const saveProfile = apiClient.updateProfile
  const saveProject = apiClient.createProject
  const saveProjectEdits = apiClient.updateProject
  const destroyProject = apiClient.deleteProject

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

    setProjectsLoading(true)
    fetchProjects()
      .then((nextProjects) => {
        if (!cancelled) {
          setProjects(nextProjects)
          setProjectsError(null)
          setProjectsLoading(false)
        }
      })
      .catch((nextError: Error) => {
        if (!cancelled) {
          setProjectsError('We could not load the projects right now. Please try again in a moment.')
          setProjectsLoading(false)
          console.error(nextError)
        }
      })

    return () => {
      cancelled = true
    }
  }, [fetchProjects])

  useEffect(() => {
    if (sessionLoading) {
      return
    }
    if (session.authenticated && (route.name === 'login' || route.name === 'register')) {
      navigateTo('/', setRoute)
      return
    }
    if (!session.authenticated && (route.name === 'projectCreate' || route.name === 'projectEdit' || route.name === 'profile')) {
      navigateTo('/login', setRoute)
    }
  }, [route, session, sessionLoading])

  function handleAuthenticated(nextSession: SessionResponse) {
    setSession(nextSession)
    navigateTo('/', setRoute)
  }

  async function handleLogout() {
    await logout()
    setSession({ authenticated: false })
    navigateTo('/', setRoute)
  }

  async function refreshProjectsAfterMutation() {
    try {
      const nextProjects = await fetchProjects()
      setProjects(nextProjects)
      setProjectsError(null)
      setProjectsLoading(false)
    } catch (nextError) {
      console.error(nextError)
    }
  }

  async function refreshSessionState() {
    try {
      setSession(await fetchSession())
    } catch (nextError) {
      console.error(nextError)
    }
  }

  async function handleCreateProject(payload: ProjectPayload) {
    const result = await saveProject(payload)
    void refreshProjectsAfterMutation()
    navigateTo(`/projects/${result.slug}?created=1`, setRoute)
  }

  async function handleUpdateProject(slug: string, payload: ProjectPayload) {
    const result = await saveProjectEdits(slug, payload)
    void refreshProjectsAfterMutation()
    navigateTo(`/projects/${result.slug}?updated=1`, setRoute)
  }

  async function handleDeleteProject(slug: string) {
    await destroyProject(slug)
    void refreshProjectsAfterMutation()
    navigateTo('/?deleted=1', setRoute)
  }

  async function handleUpdateProfile(payload: UserProfilePayload) {
    await saveProfile(payload)
    await refreshSessionState()
    void refreshProjectsAfterMutation()
  }

  return (
    <main className="page-shell">
      <header className="page-header">
        <button className="brand-lockup" type="button" onClick={() => navigateTo(homePathForRoute(route), setRoute)}>
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
              <button className="ghost-button" type="button" onClick={() => navigateTo('/projects/new', setRoute)}>
                Create project
              </button>
              <button className="ghost-button" type="button" onClick={() => navigateTo('/profile', setRoute)}>
                Profile
              </button>
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
          highlightSlug={route.highlightSlug}
          notice={route.notice}
          onOpenProject={(slug) => navigateTo(`/projects/${slug}`, setRoute)}
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

      {route.name === 'profile' ? (
        sessionLoading ? (
          <p className="state-card">Loading profile...</p>
        ) : session.authenticated ? (
          <ProfileView
            onLoadProfile={fetchProfile}
            onSubmit={handleUpdateProfile}
            onBack={() => navigateTo('/', setRoute)}
          />
        ) : null
      ) : null}

      {route.name === 'projectCreate' ? (
        <ProjectFormView
          mode="create"
          sessionUserId={session.user?.id}
          onSubmit={handleCreateProject}
          onCancel={() => navigateTo('/', setRoute)}
        />
      ) : null}

      {route.name === 'projectDetail' ? (
        <ProjectDetailView
          slug={route.slug}
          notice={route.notice}
          currentUserId={session.user?.id}
          onLoadProject={fetchProject}
          onEdit={(slug) => navigateTo(`/projects/${slug}/edit`, setRoute)}
          onDelete={handleDeleteProject}
          onBackToFeed={(slug, notice) => navigateTo(feedPathForProject(slug, notice), setRoute)}
        />
      ) : null}

      {route.name === 'projectEdit' ? (
        <ProjectFormView
          mode="edit"
          slug={route.slug}
          sessionUserId={session.user?.id}
          loadProject={fetchProject}
          onSubmit={(payload) => handleUpdateProject(route.slug, payload)}
          onCancel={() => navigateTo(`/projects/${route.slug}`, setRoute)}
        />
      ) : null}
    </main>
  )
}

type FeedViewProps = {
  projects: Project[]
  loading: boolean
  error: string | null
  highlightSlug: string | null
  notice: FeedNotice
  onOpenProject: (slug: string) => void
}

function FeedView({ projects, loading, error, highlightSlug, notice, onOpenProject }: FeedViewProps) {
  useEffect(() => {
    if (!highlightSlug || loading || error) {
      return
    }
    const frame = window.requestAnimationFrame(() => {
      const projectElement = document.querySelector<HTMLElement>(`[data-project-slug="${highlightSlug}"]`)
      projectElement?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    })
    return () => window.cancelAnimationFrame(frame)
  }, [error, highlightSlug, loading])

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

      {notice ? (
        <p className="state-card state-card--success">
          {notice === 'created'
            ? 'Your new project is live in the feed.'
            : notice === 'updated'
              ? 'Your project changes are now reflected in the feed.'
              : 'Your project has been deleted.'}
        </p>
      ) : null}

      {loading ? <p className="state-card">Loading projects...</p> : null}
      {error ? <p className="state-card state-card--error">{error}</p> : null}
      {!loading && !error && projects.length === 0 ? (
        <section className="state-shell">
          <article className="state-card">
            <strong>No projects yet.</strong>
            <p className="state-card__copy">
              The public feed will show founder commitment and open roles here once the first project is posted.
            </p>
          </article>
        </section>
      ) : null}

      {!loading && !error && projects.length > 0 ? (
        <section className="feed-grid" aria-label="Project feed">
          {projects.map((project) => (
            <ProjectCard
              key={project.id}
              project={project}
              highlighted={highlightSlug === project.slug}
              onOpen={onOpenProject}
            />
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
    } catch {
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
          Sign in to continue exploring projects and following the work you care about.
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
  const [emailPublic, setEmailPublic] = useState(false)
  const [displayName, setDisplayName] = useState('')
  const [bio, setBio] = useState('')
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
      const nextSession = await onRegister({ inviteToken, email, emailPublic, bio, displayName, password })
      onAuthenticate(nextSession)
    } catch {
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

            <label className="checkbox-field">
              <input
                checked={emailPublic}
                onChange={(event) => setEmailPublic(event.target.checked)}
                type="checkbox"
              />
              <span className="checkbox-field__box" aria-hidden="true" />
              <span className="checkbox-field__copy">
                <span className="checkbox-field__title">Make my email public</span>
                <span className="checkbox-field__hint">Show this on your profile so collaborators can reach you.</span>
              </span>
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
              Bio
              <textarea
                value={bio}
                onChange={(event) => setBio(event.target.value)}
                rows={5}
                maxLength={560}
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
  const segments = location.pathname.split('/').filter(Boolean)
  const search = new URLSearchParams(location.search)
  const detailNotice = detailNoticeFromSearch(search)

  if (location.pathname === '/login') {
    return { name: 'login' }
  }
  if (location.pathname === '/register') {
    return { name: 'register', inviteToken: search.get('invite') ?? '' }
  }
  if (location.pathname === '/profile') {
    return { name: 'profile' }
  }
  if (segments.length === 2 && segments[0] === 'projects' && segments[1] === 'new') {
    return { name: 'projectCreate' }
  }
  if (segments.length === 3 && segments[0] === 'projects' && segments[2] === 'edit') {
    return { name: 'projectEdit', slug: decodeURIComponent(segments[1]) }
  }
  if (segments.length === 2 && segments[0] === 'projects') {
    return { name: 'projectDetail', slug: decodeURIComponent(segments[1]), notice: detailNotice }
  }
  return {
    name: 'feed',
    highlightSlug: search.get('highlight'),
    notice: feedNoticeFromSearch(search),
  }
}

function detailNoticeFromSearch(search: URLSearchParams): DetailNotice {
  if (search.get('created') === '1') {
    return 'created'
  }
  if (search.get('updated') === '1') {
    return 'updated'
  }
  return null
}

function feedNoticeFromSearch(search: URLSearchParams): FeedNotice {
  if (search.get('deleted') === '1') {
    return 'deleted'
  }
  return detailNoticeFromSearch(search)
}

function homePathForRoute(route: RouteState) {
  if (route.name === 'projectDetail') {
    return feedPathForProject(route.slug, route.notice)
  }
  return '/'
}

function feedPathForProject(slug: string, notice: DetailNotice) {
  if (!notice) {
    return '/'
  }
  return `/?highlight=${encodeURIComponent(slug)}&${notice}=1`
}
