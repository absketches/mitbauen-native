import type { FormEvent } from 'react'
import { useEffect, useRef, useState } from 'react'
import { COPY, persistLanguage, readStoredLanguage, type Dictionary, type Language } from './i18n'
import {
  ApiError,
  confirmPasswordReset,
  confirmEmailVerification,
  createProjectComment,
  createProject,
  deleteAccount,
  deleteProject,
  deleteProjectImage,
  loadNotifications,
  loadProfile,
  loadPublicProfile,
  loadProjectComments,
  loadProject,
  loadProjects,
  loadSession,
  loginUser,
  logoutUser,
  markProjectCommentsRead,
  requestEmailVerification,
  requestPasswordReset,
  registerUser,
  updateProfile,
  updateProject,
  uploadProjectImage,
  validateInvite,
} from './api'
import { ProjectCard } from './components/ProjectCard'
import { ProjectDetailView } from './components/ProjectDetailView'
import { ProjectFormView } from './components/ProjectFormView'
import { ProfileView } from './components/ProfileView'
import { PublicProfileView } from './components/PublicProfileView'
import type {
  InviteValidationResponse,
  LoginPayload,
  NotificationItem,
  PasswordResetConfirmPayload,
  PasswordResetConfirmResponse,
  PasswordResetRequestPayload,
  PasswordResetRequestResponse,
  PublicUserProfile,
  Project,
  ProjectComment,
  ProjectCommentPayload,
  ProjectDetails,
  ProjectImageChanges,
  ProjectMutationResponse,
  ProjectPayload,
  RegisterPayload,
  SessionResponse,
  UserProfile,
  UserProfilePayload,
  VerificationConfirmResponse,
  VerificationEmailRequestResponse,
} from './types'

type FeedNotice = 'created' | 'updated' | 'deleted' | null
type DetailNotice = 'created' | 'updated' | 'createdWithMediaWarning' | 'updatedWithMediaWarning' | null
type VerificationNotice =
  | { tone: 'success'; message: string }
  | { tone: 'error'; message: string }
  | null

type AppApi = {
  loadProjects: () => Promise<Project[]>
  loadProject: (slug: string) => Promise<ProjectDetails>
  loadProjectComments: (slug: string) => Promise<ProjectComment[]>
  loadProfile: () => Promise<UserProfile>
  loadPublicProfile: (publicId: string) => Promise<PublicUserProfile>
  loadSession: () => Promise<SessionResponse>
  loadNotifications: () => Promise<NotificationItem[]>
  validateInvite: (token: string) => Promise<InviteValidationResponse>
  registerUser: (payload: RegisterPayload) => Promise<SessionResponse>
  loginUser: (payload: LoginPayload) => Promise<SessionResponse>
  logoutUser: () => Promise<void>
  requestEmailVerification: () => Promise<VerificationEmailRequestResponse>
  confirmEmailVerification: (token: string) => Promise<VerificationConfirmResponse>
  requestPasswordReset: (payload: PasswordResetRequestPayload) => Promise<PasswordResetRequestResponse>
  confirmPasswordReset: (payload: PasswordResetConfirmPayload) => Promise<PasswordResetConfirmResponse>
  updateProfile: (payload: UserProfilePayload) => Promise<UserProfile>
  deleteAccount: () => Promise<SessionResponse>
  createProject: (payload: ProjectPayload) => Promise<ProjectMutationResponse>
  updateProject: (slug: string, payload: ProjectPayload) => Promise<ProjectMutationResponse>
  deleteProject: (slug: string) => Promise<void>
  uploadProjectImage: (slug: string, file: File, altText?: string) => Promise<unknown>
  deleteProjectImage: (slug: string, imageId: number) => Promise<void>
  createProjectComment: (slug: string, payload: ProjectCommentPayload) => Promise<ProjectComment>
  markProjectCommentsRead: (slug: string) => Promise<{ read: boolean }>
}

type AppProps = {
  api?: Partial<AppApi>
}

type RouteState =
  | { name: 'feed'; highlightSlug: string | null; notice: FeedNotice }
  | { name: 'login' }
  | { name: 'forgotPassword' }
  | { name: 'resetPassword'; token: string }
  | { name: 'register'; inviteToken: string }
  | { name: 'verifyEmail'; token: string }
  | { name: 'profile' }
  | { name: 'publicProfile'; publicId: string }
  | { name: 'projectCreate' }
  | { name: 'projectDetail'; slug: string; notice: DetailNotice }
  | { name: 'projectEdit'; slug: string }

const defaultApi: AppApi = {
  loadProjects,
  loadProject,
  loadProjectComments,
  loadProfile,
  loadPublicProfile,
  loadSession,
  loadNotifications,
  validateInvite,
  registerUser,
  loginUser,
  logoutUser,
  requestEmailVerification,
  confirmEmailVerification,
  requestPasswordReset,
  confirmPasswordReset,
  updateProfile,
  deleteAccount,
  createProject,
  updateProject,
  deleteProject,
  uploadProjectImage,
  deleteProjectImage,
  createProjectComment,
  markProjectCommentsRead,
}

export default function App({ api }: AppProps) {
  const {
    loadProjects: fetchProjects,
    loadProject: fetchProject,
    loadProjectComments: fetchProjectComments,
    loadProfile: fetchProfile,
    loadPublicProfile: fetchPublicProfile,
    loadSession: fetchSession,
    loadNotifications: fetchNotifications,
    validateInvite: checkInvite,
    registerUser: register,
    loginUser: login,
    logoutUser: logout,
    requestEmailVerification: resendVerification,
    confirmEmailVerification: verifyEmail,
    requestPasswordReset: sendPasswordReset,
    confirmPasswordReset: resetPassword,
    updateProfile: saveProfile,
    deleteAccount: destroyAccount,
    createProject: saveProject,
    updateProject: saveProjectEdits,
    deleteProject: destroyProject,
    uploadProjectImage: saveProjectImage,
    deleteProjectImage: destroyProjectImage,
    createProjectComment: saveProjectComment,
    markProjectCommentsRead: saveProjectCommentsRead,
  } = { ...defaultApi, ...api }
  const [language, setLanguage] = useState<Language>(readStoredLanguage)
  const copy = COPY[language]

  const [route, setRoute] = useState<RouteState>(() => routeFromLocation(window.location))
  const [session, setSession] = useState<SessionResponse>({ authenticated: false })
  const [sessionLoading, setSessionLoading] = useState(true)
  const [verificationSending, setVerificationSending] = useState(false)
  const [verificationNotice, setVerificationNotice] = useState<VerificationNotice>(null)
  const [projects, setProjects] = useState<Project[]>([])
  const [projectsLoading, setProjectsLoading] = useState(true)
  const [projectsError, setProjectsError] = useState<string | null>(null)
  const emailVerificationRequired = session.authenticated && !!session.user && !session.user.emailVerified
  const notificationsEnabled = session.authenticated && !!session.user?.emailVerified
  const [notifications, setNotifications] = useState<NotificationItem[]>([])
  const [projectDetailRefreshKey, setProjectDetailRefreshKey] = useState(0)

  useEffect(() => {
    document.documentElement.lang = language
    persistLanguage(language)
  }, [language])

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
    if (!routeHasNotice(route)) {
      return
    }
    removeNoticeFromCurrentUrl()
  }, [route])

  useEffect(() => {
    if (sessionLoading) {
      return
    }
    if (!session.authenticated || !session.user?.emailVerified) {
      setProjects([])
      setProjectsError(null)
      setProjectsLoading(false)
      return
    }

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
          setProjectsError(copy.feed.error)
          setProjectsLoading(false)
          console.error(nextError)
        }
      })

    return () => {
      cancelled = true
    }
  }, [copy.feed.error, fetchProjects, session.authenticated, session.user?.emailVerified, sessionLoading])

  useEffect(() => {
    if (sessionLoading) {
      return
    }
    if (session.authenticated && (route.name === 'login' || route.name === 'register')) {
      navigateTo('/', setRoute)
      return
    }
    if (
      !session.authenticated
      && (route.name === 'projectCreate' || route.name === 'projectDetail' || route.name === 'projectEdit' || route.name === 'profile')
    ) {
      navigateTo('/login', setRoute)
    }
  }, [route, session, sessionLoading])

  useEffect(() => {
    if (session.user?.emailVerified) {
      setVerificationNotice(null)
    }
  }, [session.user?.emailVerified])

  useEffect(() => {
    if (!notificationsEnabled) {
      setNotifications([])
      return
    }

    let cancelled = false
    let intervalId: number | null = null

    const refreshIfVisible = () => {
      if (document.hidden) {
        return
      }
      refreshNotifications().catch((nextError) => {
        if (!cancelled) {
          console.error(nextError)
        }
      })
    }

    const handleVisibilityChange = () => {
      if (!document.hidden) {
        refreshIfVisible()
      }
    }

    refreshIfVisible()
    intervalId = window.setInterval(refreshIfVisible, 5000)
    document.addEventListener('visibilitychange', handleVisibilityChange)

    return () => {
      cancelled = true
      if (intervalId !== null) {
        window.clearInterval(intervalId)
      }
      document.removeEventListener('visibilitychange', handleVisibilityChange)
    }
  }, [notificationsEnabled, fetchNotifications])

  function handleAuthenticated(nextSession: SessionResponse) {
    setSession(nextSession)
    navigateTo('/', setRoute)
  }

  async function handleLogout() {
    await logout()
    setSession({ authenticated: false })
    setVerificationNotice(null)
    setNotifications([])
    setProjects([])
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

  async function refreshNotifications() {
    if (!notificationsEnabled) {
      setNotifications([])
      return
    }
    setNotifications(await fetchNotifications())
  }

  function handleOpenNotificationProject(slug: string) {
    if (route.name === 'projectDetail' && route.slug === slug) {
      setProjectDetailRefreshKey((current) => current + 1)
      return
    }
    navigateTo(`/projects/${slug}`, setRoute)
  }

  async function handleRequestEmailVerification() {
    setVerificationSending(true)
    setVerificationNotice(null)
    try {
      const result = await resendVerification()
      if (result.alreadyVerified) {
        await refreshSessionState()
        setVerificationNotice({ tone: 'success', message: copy.verification.alreadyVerified })
      } else if (result.sent) {
        setVerificationNotice({ tone: 'success', message: copy.verification.sent })
      } else {
        setVerificationNotice({ tone: 'error', message: copy.verification.dailyLimit })
      }
    } catch (nextError) {
      const message =
        nextError instanceof ApiError && nextError.code === 'AUTH_VERIFICATION_DAILY_LIMIT'
          ? copy.verification.dailyLimit
          : copy.verification.sendError
      setVerificationNotice({
        tone: 'error',
        message,
      })
    } finally {
      setVerificationSending(false)
    }
  }

  async function handleCreateProject(payload: ProjectPayload, imageChanges: ProjectImageChanges) {
    const result = await saveProject(payload)
    const mediaSaved = await tryProjectMediaMutation(() => uploadNewProjectImages(result.slug, imageChanges.newImages))
    void refreshProjectsAfterMutation()
    navigateTo(`/projects/${result.slug}?created=1${mediaSaved ? '' : '&media=failed'}`, setRoute)
  }

  async function handleUpdateProject(slug: string, payload: ProjectPayload, imageChanges: ProjectImageChanges) {
    const result = await saveProjectEdits(slug, payload)
    const mediaSaved = await tryProjectMediaMutation(async () => {
      await uploadNewProjectImages(result.slug, imageChanges.newImages)
      await Promise.all(imageChanges.removedImageIds.map((imageId) => destroyProjectImage(result.slug, imageId)))
    })
    void refreshProjectsAfterMutation()
    navigateTo(`/projects/${result.slug}?updated=1${mediaSaved ? '' : '&media=failed'}`, setRoute)
  }

  async function uploadNewProjectImages(slug: string, files: File[]) {
    for (const file of files) {
      await saveProjectImage(slug, file, '')
    }
  }

  async function tryProjectMediaMutation(mutation: () => Promise<void>) {
    try {
      await mutation()
      return true
    } catch (nextError) {
      console.error(nextError)
      return false
    }
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

  async function handleDeleteAccount() {
    const nextSession = await destroyAccount()
    setSession(nextSession)
    setVerificationNotice(null)
    setNotifications([])
    await refreshProjectsAfterMutation()
    navigateTo('/', setRoute)
  }

  return (
    <main className="page-shell">
      <header className="page-header">
        <button className="brand-lockup" type="button" onClick={() => navigateTo(homePathForRoute(route), setRoute)}>
          <img className="brand-lockup__mark" src="/mitbauen-mark.svg" alt="" />
          <span className="brand-lockup__text">
            <span className="hero__eyebrow">{copy.brand.eyebrow}</span>
            <strong>Mitbauen Lokal</strong>
          </span>
        </button>

        <div className="page-header__actions">
          <div className="language-switch" role="group" aria-label={copy.header.languageSwitchLabel}>
            <button
              className={`language-switch__button${language === 'en' ? ' language-switch__button--active' : ''}`}
              type="button"
              aria-pressed={language === 'en'}
              title={copy.header.englishLong}
              onClick={() => setLanguage('en')}
            >
              {copy.header.englishShort}
            </button>
            <button
              className={`language-switch__button${language === 'de' ? ' language-switch__button--active' : ''}`}
              type="button"
              aria-pressed={language === 'de'}
              title={copy.header.germanLong}
              onClick={() => setLanguage('de')}
            >
              {copy.header.germanShort}
            </button>
          </div>

          {sessionLoading ? <span className="page-header__status">{copy.header.loading}</span> : null}
          {!sessionLoading && session.authenticated && session.user ? (
            <>
              {notificationsEnabled ? (
                <NotificationBell
                  copy={copy.notifications}
                  notifications={notifications}
                  onOpenProject={handleOpenNotificationProject}
                  onRefresh={() => void refreshNotifications()}
                />
              ) : null}
              {session.user.emailVerified ? (
                <button className="ghost-button" type="button" onClick={() => navigateTo('/projects/new', setRoute)}>
                  {copy.header.createProject}
                </button>
              ) : null}
              <button className="ghost-button" type="button" onClick={() => navigateTo('/profile', setRoute)}>
                {copy.header.profile}
              </button>
              <span className="page-header__status">{copy.header.welcome(session.user.displayName)}</span>
              <button className="ghost-button" type="button" onClick={() => void handleLogout()}>
                {copy.header.logout}
              </button>
            </>
          ) : null}
          {!sessionLoading && !session.authenticated ? (
            <button className="ghost-button" type="button" onClick={() => navigateTo('/login', setRoute)}>
              {copy.header.signIn}
            </button>
          ) : null}
        </div>
      </header>

      {emailVerificationRequired ? (
        <section className="auth-banner" aria-live="polite">
          <div>
            <strong>{copy.verification.bannerTitle}</strong>
            <p>{copy.verification.bannerCopy}</p>
            {verificationNotice ? (
              <p className={verificationNotice.tone === 'error' ? 'auth-error' : undefined}>
                {verificationNotice.message}
              </p>
            ) : null}
          </div>

          <button className="ghost-button" type="button" onClick={() => void handleRequestEmailVerification()} disabled={verificationSending}>
            {verificationSending ? copy.verification.sending : copy.verification.resend}
          </button>
        </section>
      ) : null}

      {route.name === 'feed' ? (
        <FeedView
          copy={copy}
          projects={projects}
          loading={projectsLoading}
          error={projectsError}
          canViewProjects={session.authenticated && !!session.user?.emailVerified}
          highlightSlug={route.highlightSlug}
          notice={route.notice}
          onOpenProject={(slug) => navigateTo(`/projects/${slug}`, setRoute)}
          onOpenFounderProfile={(publicId) => navigateTo(`/users/${encodeURIComponent(publicId)}`, setRoute)}
          onSignIn={() => navigateTo('/login', setRoute)}
        />
      ) : null}

      {route.name === 'login' ? (
        <LoginView
          copy={copy.login}
          onAuthenticate={handleAuthenticated}
          onLogin={login}
          onNavigate={setRoute}
        />
      ) : null}

      {route.name === 'forgotPassword' ? (
        <ForgotPasswordView
          copy={copy.passwordReset}
          onRequestPasswordReset={sendPasswordReset}
          onNavigate={setRoute}
        />
      ) : null}

      {route.name === 'resetPassword' ? (
        <ResetPasswordView
          copy={copy.passwordReset}
          token={route.token}
          onConfirmPasswordReset={resetPassword}
          onResetComplete={() => {
            setSession({ authenticated: false })
            setVerificationNotice(null)
            setNotifications([])
          }}
          onNavigate={setRoute}
        />
      ) : null}

      {route.name === 'register' ? (
        <RegisterView
          copy={copy.register}
          inviteToken={route.inviteToken}
          onAuthenticate={handleAuthenticated}
          onNavigate={setRoute}
          onRegister={register}
          onValidateInvite={checkInvite}
        />
      ) : null}

      {route.name === 'verifyEmail' ? (
        <VerificationConfirmView
          copy={copy.verification}
          token={route.token}
          authenticated={session.authenticated}
          onConfirm={verifyEmail}
          onRefreshSession={refreshSessionState}
          onBack={() => navigateTo('/', setRoute)}
          onSignIn={() => navigateTo('/login', setRoute)}
        />
      ) : null}

      {route.name === 'profile' ? (
        sessionLoading ? (
          <p className="state-card">{copy.profile.loading}</p>
        ) : emailVerificationRequired ? (
          <VerificationRequiredView copy={copy.verification} onBack={() => navigateTo('/', setRoute)} />
        ) : session.authenticated ? (
          <ProfileView
            copy={copy.profile}
            onLoadProfile={fetchProfile}
            onSubmit={handleUpdateProfile}
            onDeleteAccount={handleDeleteAccount}
            onBack={() => navigateTo('/', setRoute)}
          />
        ) : null
      ) : null}

      {route.name === 'publicProfile' ? (
        <PublicProfileView
          copy={copy.publicProfile}
          publicId={route.publicId}
          onLoadProfile={fetchPublicProfile}
          onBack={() => navigateTo('/', setRoute)}
        />
      ) : null}

      {route.name === 'projectCreate' ? (
        sessionLoading ? (
          <p className="state-card">{copy.header.loading}</p>
        ) : emailVerificationRequired ? (
          <VerificationRequiredView copy={copy.verification} onBack={() => navigateTo('/', setRoute)} />
        ) : (
          <ProjectFormView
            copy={copy.projectForm}
            mode="create"
            onSubmit={handleCreateProject}
            onCancel={() => navigateTo('/', setRoute)}
          />
        )
      ) : null}

      {route.name === 'projectDetail' ? (
        sessionLoading ? (
          <p className="state-card">{copy.header.loading}</p>
        ) : emailVerificationRequired ? (
          <VerificationRequiredView copy={copy.verification} onBack={() => navigateTo('/', setRoute)} />
        ) : session.authenticated ? (
          <ProjectDetailView
            copy={copy.projectDetail}
            slug={route.slug}
            notice={route.notice}
            refreshKey={projectDetailRefreshKey}
            canViewComments={notificationsEnabled}
            onLoadProject={fetchProject}
            onLoadComments={fetchProjectComments}
            onCreateComment={saveProjectComment}
            onMarkCommentsRead={saveProjectCommentsRead}
            onCommentsChanged={() => void refreshNotifications()}
            onOpenFounderProfile={(publicId) => navigateTo(`/users/${encodeURIComponent(publicId)}`, setRoute)}
            onEdit={(slug) => navigateTo(`/projects/${slug}/edit`, setRoute)}
            onDelete={handleDeleteProject}
            onBackToFeed={(slug, notice) => navigateTo(feedPathForProject(slug, notice), setRoute)}
          />
        ) : null
      ) : null}

      {route.name === 'projectEdit' ? (
        sessionLoading ? (
          <p className="state-card">{copy.header.loading}</p>
        ) : emailVerificationRequired ? (
          <VerificationRequiredView copy={copy.verification} onBack={() => navigateTo(`/projects/${route.slug}`, setRoute)} />
        ) : (
          <ProjectFormView
            copy={copy.projectForm}
            mode="edit"
            slug={route.slug}
            loadProject={fetchProject}
            onSubmit={(payload, imageChanges) => handleUpdateProject(route.slug, payload, imageChanges)}
            onCancel={() => navigateTo(`/projects/${route.slug}`, setRoute)}
          />
        )
      ) : null}

      <footer className="page-footer">
        <p>{copy.footer.dataProtection}</p>
      </footer>
    </main>
  )
}

type FeedViewProps = {
  copy: Dictionary
  projects: Project[]
  loading: boolean
  error: string | null
  canViewProjects: boolean
  highlightSlug: string | null
  notice: FeedNotice
  onOpenProject: (slug: string) => void
  onOpenFounderProfile: (publicId: string) => void
  onSignIn: () => void
}

type NotificationBellProps = {
  copy: Dictionary['notifications']
  notifications: NotificationItem[]
  onOpenProject: (slug: string) => void
  onRefresh: () => void
}

function NotificationBell({ copy, notifications, onOpenProject, onRefresh }: NotificationBellProps) {
  const [open, setOpen] = useState(false)
  const bellRef = useRef<HTMLDivElement>(null)
  const count = notifications.length

  useEffect(() => {
    function handlePointerDown(event: MouseEvent) {
      if (bellRef.current && !bellRef.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handlePointerDown)
    return () => document.removeEventListener('mousedown', handlePointerDown)
  }, [])

  function handleToggle() {
    setOpen((current) => !current)
    onRefresh()
  }

  return (
    <div className="notification-bell" ref={bellRef}>
      <button
        className="notification-bell__button"
        type="button"
        aria-label={count > 0 ? copy.ariaLabelWithCount(count) : copy.ariaLabel}
        aria-expanded={open}
        onClick={handleToggle}
      >
        <svg
          className="notification-bell__icon"
          aria-hidden="true"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.9"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9" />
          <path d="M13.7 21a2 2 0 0 1-3.4 0" />
        </svg>
        {count > 0 ? <span className="notification-bell__badge">{count > 9 ? '9+' : count}</span> : null}
      </button>

      {open ? (
        <div className="notification-bell__panel">
          <div className="notification-bell__header">
            <p className="hero__eyebrow">{copy.eyebrow}</p>
            <strong>{copy.title}</strong>
          </div>

          {notifications.length === 0 ? (
            <p className="notification-bell__empty">{copy.empty}</p>
          ) : (
            <ul className="notification-bell__list">
              {notifications.map((notification) => (
                <li key={notification.id}>
                  <button
                    type="button"
                    className="notification-bell__item"
                    onClick={() => {
                      setOpen(false)
                      onOpenProject(notification.projectSlug)
                    }}
                  >
                    <span className="notification-bell__item-title">
                      {copy.commentLabel(notification.actorName, notification.projectTitle)}
                    </span>
                    <span className="notification-bell__item-body">{notification.latestBody}</span>
                    {notification.unreadCount > 1 ? (
                      <span className="notification-bell__item-count">{copy.unreadCount(notification.unreadCount)}</span>
                    ) : null}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      ) : null}
    </div>
  )
}

function FeedView({
  copy,
  projects,
  loading,
  error,
  canViewProjects,
  highlightSlug,
  notice,
  onOpenProject,
  onOpenFounderProfile,
  onSignIn,
}: FeedViewProps) {
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
          <p className="hero__eyebrow">{copy.feed.eyebrow}</p>
          <h1>{copy.feed.title}</h1>
          <p className="hero__copy">{copy.feed.copy}</p>
        </div>

        <div className="hero-stack">
          <article className="hero-panel hero-panel--dark">
            <p className="hero-panel__eyebrow">{copy.feed.buildersEyebrow}</p>
            <h2>{copy.feed.buildersTitle}</h2>
            <p>{copy.feed.buildersCopy}</p>
          </article>

          <article className="hero-panel">
            <p className="hero-panel__eyebrow">{copy.feed.snapshotEyebrow}</p>
            <p className="hero-panel__copy">{copy.feed.snapshotCopy}</p>
          </article>
        </div>
      </section>

      {notice ? (
        <p className="state-card state-card--success">
          {notice === 'created'
            ? copy.feed.noticeCreated
            : notice === 'updated'
              ? copy.feed.noticeUpdated
              : copy.feed.noticeDeleted}
        </p>
      ) : null}

      {loading ? <p className="state-card">{copy.feed.loading}</p> : null}
      {error ? <p className="state-card state-card--error">{error}</p> : null}
      {!loading && !error && !canViewProjects ? (
        <section className="public-gate" aria-labelledby="public-gate-title">
          <article className="public-gate__card">
            <p className="public-gate__eyebrow">{copy.feed.membersOnlyEyebrow}</p>
            <h2 id="public-gate-title">{copy.feed.membersOnlyTitle}</h2>
            <p>{copy.feed.membersOnlyCopy}</p>
            <div className="public-gate__actions">
              <button className="primary-button" type="button" onClick={onSignIn}>
                {copy.feed.membersOnlyAction}
              </button>
            </div>
          </article>
        </section>
      ) : null}

      {!loading && !error && canViewProjects && projects.length === 0 ? (
        <section className="state-shell">
          <article className="state-card">
            <strong>{copy.feed.emptyTitle}</strong>
            <p className="state-card__copy">{copy.feed.emptyCopy}</p>
          </article>
        </section>
      ) : null}

      {!loading && !error && canViewProjects && projects.length > 0 ? (
        <section className="feed-grid" aria-label={copy.feed.ariaLabel}>
          {projects.map((project) => (
            <ProjectCard
              copy={copy.projectCard}
              key={project.id}
              project={project}
              highlighted={highlightSlug === project.slug}
              onOpen={onOpenProject}
              onOpenFounderProfile={onOpenFounderProfile}
            />
          ))}
        </section>
      ) : null}
    </>
  )
}

type LoginViewProps = {
  copy: Dictionary['login']
  onAuthenticate: (session: SessionResponse) => void
  onLogin: (payload: LoginPayload) => Promise<SessionResponse>
  onNavigate: (route: RouteState) => void
}

function LoginView({ copy, onAuthenticate, onLogin, onNavigate }: LoginViewProps) {
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
      setError(copy.error)
      setSubmitting(false)
    }
  }

  return (
    <section className="auth-shell">
      <article className="auth-card">
        <p className="hero__eyebrow">{copy.eyebrow}</p>
        <h1>{copy.title}</h1>
        <p className="auth-card__copy">{copy.copy}</p>

        <form className="auth-form" onSubmit={handleSubmit}>
          <label>
            {copy.email}
            <input value={email} onChange={(event) => setEmail(event.target.value)} type="email" required />
          </label>

          <label>
            {copy.password}
            <input value={password} onChange={(event) => setPassword(event.target.value)} type="password" required />
          </label>

          {error ? <p className="auth-error">{error}</p> : null}

          <button className="primary-button" type="submit" disabled={submitting}>
            {submitting ? copy.submitting : copy.submit}
          </button>
        </form>

        <button className="text-button auth-card__secondary" type="button" onClick={() => navigateTo('/forgot-password', onNavigate)}>
          {copy.forgotPassword}
        </button>

        <button className="ghost-button auth-card__secondary" type="button" onClick={() => navigateTo('/', onNavigate)}>
          {copy.back}
        </button>
      </article>
    </section>
  )
}

type ForgotPasswordViewProps = {
  copy: Dictionary['passwordReset']
  onRequestPasswordReset: (payload: PasswordResetRequestPayload) => Promise<PasswordResetRequestResponse>
  onNavigate: (route: RouteState) => void
}

function ForgotPasswordView({ copy, onRequestPasswordReset, onNavigate }: ForgotPasswordViewProps) {
  const [email, setEmail] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [success, setSuccess] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setSuccess(null)
    setError(null)
    try {
      await onRequestPasswordReset({ email })
      setSuccess(copy.requestSuccess)
    } catch {
      setError(copy.requestError)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="auth-shell">
      <article className="auth-card">
        <p className="hero__eyebrow">{copy.requestEyebrow}</p>
        <h1>{copy.requestTitle}</h1>
        <p className="auth-card__copy">{copy.requestCopy}</p>

        <form className="auth-form" onSubmit={handleSubmit}>
          <label>
            {copy.email}
            <input value={email} onChange={(event) => setEmail(event.target.value)} type="email" required />
          </label>

          {success ? <p className="auth-note">{success}</p> : null}
          {error ? <p className="auth-error">{error}</p> : null}

          <button className="primary-button" type="submit" disabled={submitting}>
            {submitting ? copy.requestSubmitting : copy.requestSubmit}
          </button>
        </form>

        <button className="ghost-button auth-card__secondary" type="button" onClick={() => navigateTo('/login', onNavigate)}>
          {copy.backToLogin}
        </button>
      </article>
    </section>
  )
}

type ResetPasswordViewProps = {
  copy: Dictionary['passwordReset']
  token: string
  onConfirmPasswordReset: (payload: PasswordResetConfirmPayload) => Promise<PasswordResetConfirmResponse>
  onResetComplete: () => void
  onNavigate: (route: RouteState) => void
}

function ResetPasswordView({ copy, token, onConfirmPasswordReset, onResetComplete, onNavigate }: ResetPasswordViewProps) {
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [completed, setCompleted] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await onConfirmPasswordReset({ token, password })
      onResetComplete()
      setCompleted(true)
    } catch (nextError) {
      if (nextError instanceof ApiError && nextError.code === 'AUTH_PASSWORD_REQUIREMENTS') {
        setError(copy.weakPassword)
      } else {
        setError(copy.confirmError)
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (completed) {
    return (
      <section className="state-shell">
        <article className="state-card state-card--success">
          <strong>{copy.confirmSuccessTitle}</strong>
          <p className="state-card__copy">{copy.confirmSuccessCopy}</p>
        </article>
        <button className="ghost-button" type="button" onClick={() => navigateTo('/login', onNavigate)}>
          {copy.signIn}
        </button>
      </section>
    )
  }

  return (
    <section className="auth-shell">
      <article className="auth-card">
        <p className="hero__eyebrow">{copy.confirmEyebrow}</p>
        <h1>{copy.confirmTitle}</h1>
        <p className="auth-card__copy">{copy.confirmCopy}</p>

        <form className="auth-form" onSubmit={handleSubmit}>
          <label>
            {copy.password}
            <input value={password} onChange={(event) => setPassword(event.target.value)} type="password" minLength={8} required />
          </label>

          <p className="auth-note">{copy.passwordHint}</p>
          {error ? <p className="auth-error">{error}</p> : null}

          <button className="primary-button" type="submit" disabled={submitting || !token}>
            {submitting ? copy.confirmSubmitting : copy.confirmSubmit}
          </button>
        </form>

        <button className="ghost-button auth-card__secondary" type="button" onClick={() => navigateTo('/login', onNavigate)}>
          {copy.backToLogin}
        </button>
      </article>
    </section>
  )
}

type RegisterViewProps = {
  copy: Dictionary['register']
  inviteToken: string
  onAuthenticate: (session: SessionResponse) => void
  onNavigate: (route: RouteState) => void
  onRegister: (payload: RegisterPayload) => Promise<SessionResponse>
  onValidateInvite: (token: string) => Promise<InviteValidationResponse>
}

function RegisterView({ copy, inviteToken, onAuthenticate, onNavigate, onRegister, onValidateInvite }: RegisterViewProps) {
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
          setError(copy.inviteCheckError)
          setValidating(false)
        }
      })

    return () => {
      cancelled = true
    }
  }, [copy.inviteCheckError, inviteToken, onValidateInvite])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      const nextSession = await onRegister({ inviteToken, email, emailPublic, bio, displayName, password })
      onAuthenticate(nextSession)
    } catch {
      setError(copy.error)
      setSubmitting(false)
    }
  }

  return (
    <section className="auth-shell">
      <article className="auth-card">
        <p className="hero__eyebrow">{copy.eyebrow}</p>
        <h1>{copy.title}</h1>
        <p className="auth-card__copy">{copy.copy}</p>

        {validating ? <p className="state-card">{copy.checkingInvite}</p> : null}

        {!validating && validation?.valid ? (
          <form className="auth-form" onSubmit={handleSubmit}>
            <label>
              {copy.email}
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
                <span className="checkbox-field__title">{copy.emailPublicTitle}</span>
                <span className="checkbox-field__hint">{copy.emailPublicHint}</span>
              </span>
            </label>

            <label>
              {copy.displayName}
              <input
                value={displayName}
                onChange={(event) => setDisplayName(event.target.value)}
                type="text"
                minLength={2}
                required
              />
            </label>

            <label>
              {copy.bio}
              <textarea
                value={bio}
                onChange={(event) => setBio(event.target.value)}
                rows={5}
                maxLength={560}
              />
            </label>

            <label>
              {copy.password}
              <input
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                type="password"
                minLength={8}
                required
              />
            </label>

            <p className="auth-note">{copy.passwordHint}</p>

            {error ? <p className="auth-error">{error}</p> : null}

            <button className="primary-button" type="submit" disabled={submitting}>
              {submitting ? copy.submitting : copy.submit}
            </button>
          </form>
        ) : null}

        {!validating && validation && !validation.valid ? (
          <div className="state-card state-card--error">
            {copy.invalidInvite}
          </div>
        ) : null}

        <button className="ghost-button auth-card__secondary" type="button" onClick={() => navigateTo('/login', onNavigate)}>
          {copy.existingAccount}
        </button>
      </article>
    </section>
  )
}

type VerificationRequiredViewProps = {
  copy: Dictionary['verification']
  onBack: () => void
}

function VerificationRequiredView({ copy, onBack }: VerificationRequiredViewProps) {
  return (
    <section className="state-shell">
      <article className="state-card">
        <strong>{copy.requiredTitle}</strong>
        <p className="state-card__copy">{copy.requiredCopy}</p>
      </article>
      <button className="ghost-button" type="button" onClick={onBack}>
        {copy.back}
      </button>
    </section>
  )
}

type VerificationConfirmViewProps = {
  copy: Dictionary['verification']
  token: string
  authenticated: boolean
  onConfirm: (token: string) => Promise<VerificationConfirmResponse>
  onRefreshSession: () => Promise<void>
  onBack: () => void
  onSignIn: () => void
}

function VerificationConfirmView({
  copy,
  token,
  authenticated,
  onConfirm,
  onRefreshSession,
  onBack,
  onSignIn,
}: VerificationConfirmViewProps) {
  const [status, setStatus] = useState<'loading' | 'success' | 'error'>(token ? 'loading' : 'error')
  const [error, setError] = useState<string | null>(token ? null : copy.invalid)
  const confirmationRef = useRef<{ token: string; promise: Promise<VerificationConfirmResponse> } | null>(null)

  useEffect(() => {
    let cancelled = false

    if (!token) {
      return
    }

    if (confirmationRef.current?.token !== token) {
      confirmationRef.current = { token, promise: onConfirm(token) }
    }

    confirmationRef.current.promise
      .then(() => {
        if (!cancelled) {
          setStatus('success')
          void onRefreshSession().catch(() => undefined)
        }
      })
      .catch((nextError) => {
        if (!cancelled) {
          setStatus('error')
          setError(copy.invalid)
        }
      })

    return () => {
      cancelled = true
    }
  }, [copy.invalid, onConfirm, onRefreshSession, token])

  if (status === 'loading') {
    return (
      <section className="state-shell">
        <article className="state-card">
          <strong>{copy.loadingTitle}</strong>
          <p className="state-card__copy">{copy.loadingCopy}</p>
        </article>
      </section>
    )
  }

  if (status === 'success') {
    return (
      <section className="state-shell">
        <article className="state-card state-card--success">
          <strong>{copy.successTitle}</strong>
          <p className="state-card__copy">{authenticated ? copy.successCopyRefresh : copy.successCopySignIn}</p>
        </article>
        <button className="ghost-button" type="button" onClick={authenticated ? onBack : onSignIn}>
          {authenticated ? copy.refresh : copy.signIn}
        </button>
      </section>
    )
  }

  return (
    <section className="state-shell">
      <article className="state-card state-card--error">
        <strong>{copy.errorTitle}</strong>
        <p className="state-card__copy">{error ?? copy.invalid}</p>
      </article>
      <button className="ghost-button" type="button" onClick={authenticated ? onBack : onSignIn}>
        {authenticated ? copy.back : copy.signIn}
      </button>
    </section>
  )
}

function navigateTo(path: string, onNavigate: (route: RouteState) => void) {
  window.history.pushState({}, '', path)
  onNavigate(routeFromLocation(window.location))
}

function routeHasNotice(route: RouteState) {
  return (route.name === 'feed' || route.name === 'projectDetail') && route.notice !== null
}

function removeNoticeFromCurrentUrl() {
  const url = new URL(window.location.href)
  url.searchParams.delete('created')
  url.searchParams.delete('updated')
  url.searchParams.delete('deleted')
  url.searchParams.delete('media')
  window.history.replaceState({}, '', `${url.pathname}${url.search}${url.hash}`)
}

function routeFromLocation(location: Location): RouteState {
  const segments = location.pathname.split('/').filter(Boolean)
  const search = new URLSearchParams(location.search)
  const detailNotice = detailNoticeFromSearch(search)

  if (location.pathname === '/login') {
    return { name: 'login' }
  }
  if (location.pathname === '/forgot-password') {
    return { name: 'forgotPassword' }
  }
  if (location.pathname === '/reset-password') {
    return { name: 'resetPassword', token: search.get('token') ?? '' }
  }
  if (location.pathname === '/register') {
    return { name: 'register', inviteToken: search.get('invite') ?? '' }
  }
  if (location.pathname === '/verify-email') {
    return { name: 'verifyEmail', token: search.get('token') ?? '' }
  }
  if (location.pathname === '/profile') {
    return { name: 'profile' }
  }
  if (segments.length === 2 && segments[0] === 'users') {
    return { name: 'publicProfile', publicId: decodeURIComponent(segments[1]) }
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
    return search.get('media') === 'failed' ? 'createdWithMediaWarning' : 'created'
  }
  if (search.get('updated') === '1') {
    return search.get('media') === 'failed' ? 'updatedWithMediaWarning' : 'updated'
  }
  return null
}

function feedNoticeFromSearch(search: URLSearchParams): FeedNotice {
  if (search.get('deleted') === '1') {
    return 'deleted'
  }
  if (search.get('created') === '1') {
    return 'created'
  }
  if (search.get('updated') === '1') {
    return 'updated'
  }
  return null
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
  const noticeKey = notice.startsWith('created') ? 'created' : 'updated'
  return `/?highlight=${encodeURIComponent(slug)}&${noticeKey}=1`
}
