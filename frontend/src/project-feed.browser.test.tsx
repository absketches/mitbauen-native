/// <reference types="@vitest/browser/matchers" />
/// <reference types="@vitest/browser/providers/playwright" />

import { beforeEach, expect, test, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { StrictMode } from 'react'
import App from './App'
import { ApiError } from './api'
import type {
  InviteValidationResponse,
  JobListing,
  NotificationItem,
  Project,
  ProjectComment,
  ProjectDetails,
  ProjectPayload,
  SessionResponse,
} from './types'

const baseProjects: Project[] = [
  {
    id: 1,
    slug: 'solar-for-neighbors',
    title: 'Solar For Neighbors',
    descriptions: {
      de: 'Ein kooperatives Toolkit für Wohnblocks, um Balkonsolar-Installationen zu koordinieren, Dachbedingungen zu dokumentieren und realistische Einsparungen zu vergleichen.',
      en: 'A cooperative toolkit for apartment blocks to coordinate balcony solar installs, document rooftop constraints and help neighbors compare realistic energy savings before they commit to a shared rollout.',
    },
    status: 'active',
    founder: {
      publicId: 'usr_avery_bloom_01',
      name: 'Avery Bloom',
      role: 'Founder + Product',
      commitment: '10 hrs/week',
    },
    openRoles: [
      { id: 11, title: 'Android Engineer', commitment: '6 hrs/week' },
      { id: 12, title: 'Community Researcher', commitment: '4 hrs/week' },
    ],
    createdAt: '2026-04-22T09:00:00Z',
  },
  {
    id: 2,
    slug: 'neighborhood-tool-library',
    title: 'Neighborhood Tool Library',
    descriptions: {
      de: 'Eine einfache Möglichkeit für Nachbarinnen und Nachbarn, Werkzeuge zu teilen, Buchungsfenster zu koordinieren und Reparaturwissen festzuhalten.',
      en: 'A simple way for neighbors to share tools, coordinate booking windows and capture repair know-how so the same drill, ladder or sewing machine can stay useful across an entire street.',
    },
    status: 'active',
    founder: {
      publicId: 'usr_nora_patel_01',
      name: 'Nora Patel',
      role: 'Founder + Ops',
      commitment: '8 hrs/week',
    },
    openRoles: [
      { id: 21, title: 'Frontend Engineer', commitment: '5 hrs/week' },
      { id: 22, title: 'Partnerships Lead', commitment: '3 hrs/week' },
    ],
    createdAt: '2026-04-20T14:30:00Z',
  },
]

const baseProjectDetails: ProjectDetails = {
  ...baseProjects[0],
  canManage: false,
  updatedAt: '2026-04-22T09:00:00Z',
}

const baseComments: ProjectComment[] = [
  {
    id: 1,
    body: 'This discussion is visible to verified members.',
    authorPublicId: 'usr_nora_patel_01',
    authorDisplayName: 'Nora Patel',
    createdAt: '2026-04-23T09:00:00Z',
  },
]

const baseNotifications: NotificationItem[] = [
  {
    id: 'project-comment-solar-for-neighbors',
    type: 'project_comment',
    projectSlug: 'solar-for-neighbors',
    projectTitle: 'Solar For Neighbors',
    actorName: 'Nora Patel',
    latestBody: 'This discussion is visible to verified members.',
    latestAt: '2026-04-23T09:00:00Z',
    unreadCount: 1,
  },
]

const baseJobs: JobListing[] = [
  {
    id: 'solar-for-neighbors::Android Engineer',
    roleId: 11,
    projectSlug: 'solar-for-neighbors',
    projectTitle: 'Solar For Neighbors',
    roleTitle: 'Android Engineer',
    roleCommitment: 'Help build the first mobile workflow for neighborhood solar planning.',
  },
  {
    id: 'neighborhood-tool-library::Partnerships Lead',
    roleId: 22,
    projectSlug: 'neighborhood-tool-library',
    projectTitle: 'Neighborhood Tool Library',
    roleTitle: 'Partnerships Lead',
    roleCommitment: 'Coordinate local partners and keep the first lending locations aligned.',
  },
]

beforeEach(() => {
  window.localStorage.clear()
})

test('keeps project feed private for anonymous visitors in browser mode', async () => {
  window.history.pushState({}, '', '/')
  const loadProjects = vi.fn(async () => [])

  const screen = await render(
    <App
      api={{
        loadProjects,
        loadSession: async (): Promise<SessionResponse> => ({ authenticated: false }),
      }}
    />,
  )

  await expect.element(screen.getByText('Finde Projekte mit konkretem Bedarf.')).toBeVisible()
  await expect.element(screen.getByText('Builders Inside')).toBeVisible()
  await expect.element(screen.getByText('Lokale Projekte liegen hinter der Mitgliedertür.')).toBeVisible()
  await expect.element(screen.getByText('Melde dich an und bestätige deine E-Mail-Adresse, um den aktiven Projektraum zu sehen.')).toBeVisible()
  await expect.element(screen.getByText('Alle Plattformdaten werden in Deutschland gespeichert und gemäß EU-Datenschutzstandards behandelt.')).toBeVisible()
  await expect.element(screen.getByRole('button', { name: 'Anmelden' }).nth(0)).toBeVisible()
  expect(loadProjects).not.toHaveBeenCalled()
  expect(document.documentElement.lang).toBe('de')
  expect(document.body.textContent).not.toContain('Join Mitbauen.')
})

test('honors an explicit English language selection', async () => {
  window.localStorage.setItem('mitbauen_language', 'en')
  window.history.pushState({}, '', '/')

  const screen = await render(
    <App
      api={{
        loadProjects: async () => [],
        loadSession: async (): Promise<SessionResponse> => ({ authenticated: false }),
      }}
    />,
  )

  await expect.element(screen.getByText('Find projects that need contributors.')).toBeVisible()
  await expect.element(screen.getByText('Builders Inside')).toBeVisible()
  await expect.element(screen.getByText('Local projects live behind the member door.')).toBeVisible()
  await expect.element(screen.getByRole('button', { name: 'Sign in' }).nth(0)).toBeVisible()
  expect(document.documentElement.lang).toBe('en')
})

test('shows project descriptions for the selected language only', async () => {
  window.history.pushState({}, '', '/')
  const localizedProject: Project = {
    ...baseProjects[0],
    descriptions: {
      de: null,
      en: 'English project description written by the creator.',
    },
  }

  const screen = await render(
    <App
      api={{
        loadProjects: async () => [localizedProject],
        loadNotifications: async () => [],
        loadSession: async (): Promise<SessionResponse> => ({
          authenticated: true,
          user: { displayName: 'Alex Builder', email: 'alex@example.test', emailVerified: true },
        }),
      }}
    />,
  )

  await expect.element(screen.getByText('Keine deutsche Beschreibung verfügbar.')).toBeVisible()
  await screen.getByRole('button', { name: 'EN', exact: true }).click()
  await expect.element(screen.getByText('English project description written by the creator.')).toBeVisible()
})

test('shows translated project descriptions with a disclaimer', async () => {
  window.history.pushState({}, '', '/')
  const localizedProject: Project = {
    ...baseProjects[0],
    descriptions: {
      de: null,
      en: 'English project description written by the creator.',
    },
    descriptionViews: {
      de: {
        text: 'Deutsche Beschreibung, die durch ein Tool erzeugt wurde.',
        language: 'de',
        originalLanguage: 'en',
        translated: true,
        source: 'tool_translation',
      },
      en: {
        text: 'English project description written by the creator.',
        language: 'en',
        originalLanguage: 'en',
        translated: false,
        source: 'original',
      },
    },
  }

  const screen = await render(
    <App
      api={{
        loadProjects: async () => [localizedProject],
        loadNotifications: async () => [],
        loadSession: async (): Promise<SessionResponse> => ({
          authenticated: true,
          user: { displayName: 'Alex Builder', email: 'alex@example.test', emailVerified: true },
        }),
      }}
    />,
  )

  await expect.element(screen.getByText('Deutsche Beschreibung, die durch ein Tool erzeugt wurde.')).toBeVisible()
  await expect.element(screen.getByText('Mit einem Tool übersetzt. Diese Version wurde nicht von der erstellenden Person bereitgestellt.')).toBeVisible()
  expect(document.body.textContent).not.toContain('Keine deutsche Beschreibung verfügbar.')
})

test('shows open roles on the jobs page for verified members', async () => {
  window.history.pushState({}, '', '/jobs')
  window.localStorage.setItem('mitbauen_language', 'en')
  const loadJobs = vi.fn(async () => [baseJobs[0]])
  const applyForJob = vi.fn(async () => ({ sent: true }))

  const screen = await render(
    <App
      api={{
        loadProjects: async () => baseProjects,
        loadJobs,
        applyForJob,
        loadNotifications: async () => [],
        loadSession: async (): Promise<SessionResponse> => ({
          authenticated: true,
          user: { displayName: 'Alex Builder', email: 'alex@example.test', emailVerified: true },
        }),
        loadProject: async (): Promise<ProjectDetails> => baseProjectDetails,
        loadProjectComments: async () => [],
      }}
    />,
  )

  await expect.element(screen.getByText('Android Engineer')).toBeVisible()
  await expect.element(screen.getByRole('button', { name: 'Solar For Neighbors' })).toBeVisible()
  await expect.element(screen.getByText('Help build the first mobile workflow for neighborhood solar planning.')).toBeVisible()
  expect(loadJobs).toHaveBeenCalled()

  await screen.getByRole('button', { name: 'Apply' }).click()
  await screen.getByLabelText('How do you fit this role?').fill('I have built Android planning workflows and can help this role move quickly.')
  await screen.getByLabelText('Availability').fill('Two evenings per week.')
  await screen.getByRole('button', { name: 'Send application' }).click()
  expect(applyForJob).toHaveBeenCalledWith({
    roleId: 11,
    fit: 'I have built Android planning workflows and can help this role move quickly.',
    availability: 'Two evenings per week.',
  })
  await expect.element(screen.getByText('Application sent to the project creator.')).toBeVisible()
})

test('renders the invite-only registration view for an open invite', async () => {
  window.history.pushState({}, '', '/register?invite=test-open-invite')

  const screen = await render(
    <App
      api={{
        loadProjects: async () => baseProjects,
        loadSession: async (): Promise<SessionResponse> => ({ authenticated: false }),
        validateInvite: async (): Promise<InviteValidationResponse> => ({
          valid: true,
        }),
      }}
    />,
  )

  await expect.element(screen.getByText('Konto erstellen.')).toBeVisible()
  await expect.element(screen.getByRole('textbox', { name: 'E-Mail' })).toHaveValue('')
  await expect.element(screen.getByRole('button', { name: 'Konto erstellen' })).toBeVisible()
})

test('requests and confirms a password reset', async () => {
  window.history.pushState({}, '', '/login')
  const requestPasswordReset = vi.fn(async () => ({ requested: true }))
  const confirmPasswordReset = vi.fn(async () => ({ reset: true }))

  const screen = await render(
    <App
      api={{
        loadProjects: async () => baseProjects,
        loadSession: async (): Promise<SessionResponse> => ({ authenticated: false }),
        requestPasswordReset,
        confirmPasswordReset,
      }}
    />,
  )

  await screen.getByRole('button', { name: 'Passwort vergessen?' }).click()
  await screen.getByRole('textbox', { name: 'E-Mail' }).fill('alex@example.test')
  await screen.getByRole('button', { name: 'Reset-Link senden' }).click()

  await expect.element(screen.getByText('Falls ein Konto für diese E-Mail existiert, wurde ein Reset-Link gesendet.')).toBeVisible()
  expect(requestPasswordReset).toHaveBeenCalledWith({ email: 'alex@example.test' })

  window.history.pushState({}, '', '/reset-password?token=reset_browser')
  window.dispatchEvent(new PopStateEvent('popstate'))
  await screen.getByLabelText('Neues Passwort').fill('NewSecure1')
  await screen.getByRole('button', { name: 'Passwort ändern' }).click()

  await expect.element(screen.getByText('Passwort geändert.')).toBeVisible()
  expect(confirmPasswordReset).toHaveBeenCalledWith({ token: 'reset_browser', password: 'NewSecure1' })
})

test('confirms email verification and leaves the loading state in strict mode', async () => {
  window.history.pushState({}, '', '/verify-email?token=verify_browser')
  const confirmEmailVerification = vi.fn(async () => ({ verified: true }))

  const screen = await render(
    <StrictMode>
      <App
        api={{
          loadProjects: async () => baseProjects,
          loadSession: async (): Promise<SessionResponse> => ({ authenticated: false }),
          confirmEmailVerification,
        }}
      />
    </StrictMode>,
  )

  await expect.element(screen.getByText('E-Mail bestätigt.')).toBeVisible()
  await expect.element(screen.getByText('Deine E-Mail-Adresse wurde bestätigt. Du kannst dich jetzt anmelden.')).toBeVisible()
  expect(document.body.textContent).not.toContain('E-Mail-Adresse wird bestätigt...')
  expect(confirmEmailVerification).toHaveBeenCalledOnce()
  expect(confirmEmailVerification).toHaveBeenCalledWith('verify_browser')
})

test('clears stale unverified session state after password reset', async () => {
  window.history.pushState({}, '', '/reset-password?token=reset_browser')
  const confirmPasswordReset = vi.fn(async () => ({ reset: true }))

  const screen = await render(
    <App
      api={{
        loadProjects: async () => baseProjects,
        loadSession: async (): Promise<SessionResponse> => ({
          authenticated: true,
          user: { displayName: 'Alex Builder', email: 'alex@example.test', emailVerified: false },
        }),
        confirmPasswordReset,
      }}
    />,
  )

  await expect.element(screen.getByText('Bestätige deine E-Mail-Adresse.')).toBeVisible()
  await screen.getByLabelText('Neues Passwort').fill('NewSecure1')
  await screen.getByRole('button', { name: 'Passwort ändern' }).click()

  await expect.element(screen.getByText('Passwort geändert.')).toBeVisible()
  expect(document.body.textContent).not.toContain('Bestätige deine E-Mail-Adresse.')
})

test('opens a founder profile from the public feed', async () => {
  window.history.pushState({}, '', '/')

  const screen = await render(
    <App
      api={{
        loadProjects: async () => baseProjects,
        loadPublicProfile: async (_publicId) => ({
          displayName: 'Avery Bloom',
          bio: 'Helping neighbors move ideas into real, founder-led experiments.',
          email: null,
        }),
        loadNotifications: async () => [],
        loadSession: async (): Promise<SessionResponse> => ({
          authenticated: true,
          user: { displayName: 'Alex Builder', email: 'alex@example.test', emailVerified: true },
        }),
      }}
    />,
  )

  await screen.getByRole('button', { name: 'Avery Bloom' }).click()

  await expect.element(screen.getByRole('heading', { name: 'Avery Bloom' })).toBeVisible()
  await expect.element(screen.getByText('Helping neighbors move ideas into real, founder-led experiments.')).toBeVisible()
})

test('shows localized copy for deleted public profiles', async () => {
  window.history.pushState({}, '', '/users/usr_avery_bloom_01')

  const screen = await render(
    <App
      api={{
        loadPublicProfile: async () => {
          throw new ApiError('Request failed (410)', 410, 'USER_DELETED')
        },
        loadSession: async (): Promise<SessionResponse> => ({ authenticated: false }),
      }}
    />,
  )

  await expect.element(screen.getByText('Dieser Nutzer existiert nicht mehr.')).toBeVisible()
})

test('shows notifications and project comments for verified members', async () => {
  window.history.pushState({}, '', '/projects/solar-for-neighbors')

  const markCommentsRead = vi.fn(async () => ({ read: true }))
  const createComment = vi.fn(async (_slug: string, payload: { body: string }): Promise<ProjectComment> => ({
    id: 2,
    body: payload.body,
    authorPublicId: 'usr_alex_builder_01',
    authorDisplayName: 'Alex Builder',
    createdAt: '2026-04-23T10:00:00Z',
  }))
  const refreshedComments = [
    ...baseComments,
    {
      id: 3,
      body: 'Loaded after opening the same-project notification.',
      authorPublicId: 'usr_nora_patel_01',
      authorDisplayName: 'Nora Patel',
      createdAt: '2026-04-23T11:00:00Z',
    },
  ]
  let commentsLoadCount = 0

  const screen = await render(
    <App
      api={{
        loadProjects: async () => baseProjects,
        loadProject: async () => baseProjectDetails,
        loadProjectComments: async () => {
          commentsLoadCount += 1
          return commentsLoadCount > 1 ? refreshedComments : baseComments
        },
        markProjectCommentsRead: markCommentsRead,
        createProjectComment: createComment,
        loadNotifications: async () => baseNotifications,
        loadSession: async (): Promise<SessionResponse> => ({
          authenticated: true,
          user: { displayName: 'Alex Builder', email: 'alex@example.test', emailVerified: true },
        }),
      }}
    />,
  )

  await expect.element(screen.getByRole('heading', { name: 'Solar For Neighbors' })).toBeVisible()
  await expect.element(screen.getByText('This discussion is visible to verified members.')).toBeVisible()
  await expect.element(screen.getByRole('button', { name: '1 Benachrichtigungen' })).toBeVisible()

  await screen.getByRole('button', { name: '1 Benachrichtigungen' }).click()
  await expect.element(screen.getByText('Nora Patel hat Solar For Neighbors kommentiert')).toBeVisible()
  await screen.getByRole('button', { name: /Nora Patel hat Solar For Neighbors kommentiert/ }).click()
  await expect.element(screen.getByText('Loaded after opening the same-project notification.')).toBeVisible()
  expect(commentsLoadCount).toBeGreaterThan(1)

  await screen.getByLabelText('Schreibe einen Kommentar...').fill('A fresh browser-mode comment.')
  await screen.getByRole('button', { name: 'Kommentar posten' }).click()

  await expect.element(screen.getByText('A fresh browser-mode comment.')).toBeVisible()
  expect(createComment).toHaveBeenCalledWith('solar-for-neighbors', { body: 'A fresh browser-mode comment.' })
  expect(markCommentsRead).toHaveBeenCalled()
})

test('renders safe markdown for project details and comments', async () => {
  window.history.pushState({}, '', '/projects/solar-for-neighbors')

  const markdownProject: ProjectDetails = {
    ...baseProjectDetails,
    descriptions: {
      de: 'First paragraph with **bold text**.\nSecond line with [a safe link](https://example.com).',
      en: null,
    },
    founder: {
      ...baseProjectDetails.founder,
      commitment: '- Host weekly sessions\n- Share *clear notes*',
    },
    openRoles: [
      {
        id: 31,
        title: 'Frontend Engineer',
        commitment: 'Build **member flows** and [docs](https://example.com/docs).',
      },
    ],
  }

  const screen = await render(
    <App
      api={{
        loadProjects: async () => [{ ...markdownProject }],
        loadProject: async () => markdownProject,
        loadProjectComments: async () => [
          {
            id: 7,
            body: 'Comment with **bold** and [link](https://example.com/comment).',
            authorPublicId: 'usr_nora_patel_01',
            authorDisplayName: 'Nora Patel',
            createdAt: '2026-04-23T09:00:00Z',
          },
        ],
        markProjectCommentsRead: async () => ({ read: true }),
        loadNotifications: async () => [],
        loadSession: async (): Promise<SessionResponse> => ({
          authenticated: true,
          user: { displayName: 'Alex Builder', email: 'alex@example.test', emailVerified: true },
        }),
      }}
    />,
  )

  await expect.element(screen.getByText('bold text')).toBeVisible()
  await expect.element(screen.getByRole('link', { name: 'a safe link' })).toHaveAttribute('href', 'https://example.com/')
  await expect.element(screen.getByText('clear notes')).toBeVisible()
  await expect.element(screen.getByRole('link', { name: 'docs' })).toHaveAttribute('href', 'https://example.com/docs')
  await expect.element(screen.getByText('bold', { exact: true })).toBeVisible()
  await expect.element(screen.getByRole('link', { name: 'link', exact: true })).toHaveAttribute('href', 'https://example.com/comment')
  await expect.element(screen.getByText('Markdown wird unterstützt: Zeilenumbrüche, **fett**, *kursiv*, Listen und Links.')).toBeVisible()
})

test('validates the create project form for authenticated users', async () => {
  window.history.pushState({}, '', '/projects/new')

  const createProjectMock = vi.fn(async (_payload: ProjectPayload) => ({ slug: 'ignored' }))

  const screen = await render(
    <App
      api={{
        loadProjects: async () => baseProjects,
        loadSession: async (): Promise<SessionResponse> => ({
          authenticated: true,
          user: { displayName: 'Alex Builder', email: 'alex@example.test', emailVerified: true },
        }),
        loadNotifications: async () => [],
        createProject: createProjectMock,
      }}
    />,
  )

  await screen.getByRole('textbox', { name: 'Titel', exact: true }).fill('Tiny')
  await screen.getByRole('textbox', { name: 'Beschreibung', exact: true }).fill('Too short to pass.')
  await screen.getByLabelText('Deine Rolle in diesem Projekt').fill('Go')
  await screen.getByLabelText('Wozu du dich persönlich verpflichtest').fill('No')
  await screen.getByLabelText('Titel für Rolle 1').fill('No')
  await screen.getByLabelText('Commitment für Rolle 1').fill('No')
  await screen.getByRole('button', { name: 'Projekt erstellen' }).nth(1).click()

  await expect.element(screen.getByText('Gib einen Projekttitel zwischen 5 und 120 Zeichen ein.')).toBeVisible()
  await expect.element(screen.getByText('Gib eine Projektbeschreibung zwischen 40 und 3000 Zeichen ein.')).toBeVisible()
  await expect.element(screen.getByText('Gib eine Gründerrolle zwischen 3 und 120 Zeichen ein.')).toBeVisible()
  await expect.element(screen.getByText('Gib ein Gründer-Commitment zwischen 5 und 500 Zeichen ein.')).toBeVisible()
  await expect.element(screen.getByText('Gib einen Rollentitel zwischen 3 und 120 Zeichen ein.')).toBeVisible()
  await expect.element(screen.getByText('Gib ein Rollen-Commitment zwischen 3 und 500 Zeichen ein.')).toBeVisible()
  expect(createProjectMock).not.toHaveBeenCalled()
})

test('opens the populated description tab when editing an older English-only project', async () => {
  window.history.pushState({}, '', '/projects/solar-for-neighbors/edit')
  const englishDescription =
    'An English project description migrated from the original single description field so the editor should open the English tab.'
  const editableProject: ProjectDetails = {
    ...baseProjectDetails,
    canManage: true,
    descriptions: {
      de: null,
      en: englishDescription,
    },
  }

  const screen = await render(
    <App
      api={{
        loadProjects: async () => baseProjects,
        loadProject: async () => editableProject,
        loadNotifications: async () => [],
        loadSession: async (): Promise<SessionResponse> => ({
          authenticated: true,
          user: { displayName: 'Alex Builder', email: 'alex@example.test', emailVerified: true },
        }),
      }}
    />,
  )

  await expect.element(screen.getByRole('heading', { name: 'Projekt bearbeiten.' })).toBeVisible()
  await expect.element(screen.getByRole('tab', { name: 'EN' })).toHaveAttribute('aria-selected', 'true')
  await expect.element(screen.getByRole('textbox', { name: 'Beschreibung', exact: true })).toHaveValue(englishDescription)
})

test('shows description validation errors for inactive language tabs', async () => {
  window.history.pushState({}, '', '/projects/new')
  const createProjectMock = vi.fn(async (_payload: ProjectPayload) => ({ slug: 'ignored' }))

  const screen = await render(
    <App
      api={{
        loadProjects: async () => baseProjects,
        loadSession: async (): Promise<SessionResponse> => ({
          authenticated: true,
          user: { displayName: 'Alex Builder', email: 'alex@example.test', emailVerified: true },
        }),
        loadNotifications: async () => [],
        createProject: createProjectMock,
      }}
    />,
  )

  await screen.getByRole('textbox', { name: 'Titel', exact: true }).fill('Localized Validation Project')
  await screen.getByRole('textbox', { name: 'Beschreibung', exact: true }).fill(
    'Eine deutsche Projektbeschreibung mit genug Details, damit nur der englische Tab einen Fehler auslöst.',
  )
  await screen.getByRole('tab', { name: 'EN' }).click()
  await screen.getByRole('textbox', { name: 'Beschreibung', exact: true }).fill('Too short.')
  await screen.getByRole('tab', { name: 'DE' }).click()
  await screen.getByLabelText('Deine Rolle in diesem Projekt').fill('Founder + Editor')
  await screen.getByLabelText('Wozu du dich persönlich verpflichtest').fill(
    'I am keeping the bilingual project information accurate and useful.',
  )
  await screen.getByLabelText('Titel für Rolle 1').fill('Language Reviewer')
  await screen.getByLabelText('Commitment für Rolle 1').fill('Review translated project details.')
  await screen.getByRole('button', { name: 'Projekt erstellen' }).nth(1).click()

  await expect.element(screen.getByText('EN: Gib eine Projektbeschreibung zwischen 40 und 3000 Zeichen ein.')).toBeVisible()
  expect(createProjectMock).not.toHaveBeenCalled()
})

test('creates a project, lands on detail, and returns to a highlighted feed card', async () => {
  window.history.pushState({}, '', '/projects/new')

  let createdProject: ProjectDetails | null = null
  let submittedPayload: ProjectPayload | null = null
  let projects = [...baseProjects]

  const screen = await render(
    <App
      api={{
        loadProjects: async () => projects,
        loadProjectComments: async () => [],
        markProjectCommentsRead: async () => ({ read: true }),
        loadNotifications: async () => [],
        loadProject: async (slug: string) => {
          if (createdProject?.slug === slug) {
            return createdProject
          }
          throw new Error('Project not found.')
        },
        loadSession: async (): Promise<SessionResponse> => ({
          authenticated: true,
          user: { displayName: 'Alex Builder', email: 'alex@example.test', emailVerified: true },
        }),
        createProject: async (payload: ProjectPayload) => {
          submittedPayload = payload
          createdProject = {
            id: 99,
            canManage: true,
            slug: 'circular-kitchen-atlas',
            title: payload.title,
            descriptions: payload.descriptions,
            status: 'active',
            founder: {
              publicId: 'usr_alex_builder_01',
              name: 'Alex Builder',
              role: payload.founderRole,
              commitment: payload.founderCommitment,
            },
            openRoles: payload.openRoles,
            links: payload.links,
            images: [],
            createdAt: '2026-04-29T10:00:00Z',
            updatedAt: '2026-04-29T10:00:00Z',
          }
          projects = [
            {
              id: createdProject.id,
              slug: createdProject.slug,
              title: createdProject.title,
              descriptions: createdProject.descriptions,
              status: createdProject.status,
              founder: createdProject.founder,
              openRoles: createdProject.openRoles,
              links: createdProject.links,
              images: createdProject.images,
              createdAt: createdProject.createdAt,
            },
            ...projects,
          ]
          return { slug: createdProject.slug }
        },
      }}
    />,
  )

  await screen.getByRole('textbox', { name: 'Titel', exact: true }).fill('Circular Kitchen Atlas')
  await screen.getByRole('textbox', { name: 'Beschreibung', exact: true }).fill(
    'A living guide for neighborhood kitchens that want to map surplus food, shared prep capacity and the fastest path from extra ingredients to community meals.',
  )
  await screen.getByLabelText('Deine Rolle in diesem Projekt').fill('Founder + Community Ops')
  await screen.getByLabelText('Wozu du dich persönlich verpflichtest').fill(
    'I am already running the pilot dinners, documenting learnings and coordinating the volunteer operations myself.',
  )
  await screen.getByLabelText('Titel für Rolle 1').fill('Frontend Engineer')
  await screen.getByLabelText('Commitment für Rolle 1').fill('Build the first contributor-facing workflows.')
  await screen.getByRole('button', { name: 'Link hinzufügen' }).click()
  await screen.getByLabelText('Label für Link 1').fill('Website')
  await screen.getByLabelText('URL für Link 1').fill('https://example.com/kitchen')
  await screen.getByRole('button', { name: 'Link hinzufügen' }).click()
  await screen.getByLabelText('Label für Link 2').fill('GitHub')
  await screen.getByLabelText('URL für Link 2').fill('https://github.com/absketches')
  await screen.getByRole('button', { name: 'Projekt erstellen' }).nth(1).click()

  await expect.element(screen.getByRole('heading', { name: 'Circular Kitchen Atlas' })).toBeVisible()
  await expect.element(screen.getByText('Projekt erstellt.')).toBeVisible()
  await expect.element(screen.getByRole('link', { name: 'Website' })).toHaveAttribute('href', 'https://example.com/kitchen')
  expect(submittedPayload?.links).toEqual([
    { label: 'Website', url: 'https://example.com/kitchen' },
    { label: 'GitHub', url: 'https://github.com/absketches' },
  ])
  expect(submittedPayload?.descriptions.en).toBeNull()
  expect(submittedPayload?.descriptions.de).toContain('surplus food')

  await screen.getByRole('button', { name: 'Zurück zu den Projekten' }).click()

  await expect.element(screen.getByText('Projekt veröffentlicht.')).toBeVisible()
  await expect.element(screen.getByTestId('project-circular-kitchen-atlas')).toBeVisible()
})

test('shows a detail warning when project image upload fails after creation', async () => {
  window.history.pushState({}, '', '/projects/new')

  let createdProject: ProjectDetails | null = null
  let projects = [...baseProjects]
  const uploadProjectImage = vi.fn(async () => {
    throw new Error('Image upload failed.')
  })

  const screen = await render(
    <App
      api={{
        loadProjects: async () => projects,
        loadProjectComments: async () => [],
        markProjectCommentsRead: async () => ({ read: true }),
        loadNotifications: async () => [],
        loadProject: async (slug: string) => {
          if (createdProject?.slug === slug) {
            return createdProject
          }
          throw new Error('Project not found.')
        },
        loadSession: async (): Promise<SessionResponse> => ({
          authenticated: true,
          user: { displayName: 'Alex Builder', email: 'alex@example.test', emailVerified: true },
        }),
        createProject: async (payload: ProjectPayload) => {
          createdProject = {
            id: 100,
            canManage: true,
            slug: 'image-warning-project',
            title: payload.title,
            descriptions: payload.descriptions,
            status: 'active',
            founder: {
              publicId: 'usr_alex_builder_01',
              name: 'Alex Builder',
              role: payload.founderRole,
              commitment: payload.founderCommitment,
            },
            openRoles: payload.openRoles,
            links: payload.links,
            images: [],
            createdAt: '2026-04-29T10:00:00Z',
            updatedAt: '2026-04-29T10:00:00Z',
          }
          projects = [
            {
              id: createdProject.id,
              slug: createdProject.slug,
              title: createdProject.title,
              descriptions: createdProject.descriptions,
              status: createdProject.status,
              founder: createdProject.founder,
              openRoles: createdProject.openRoles,
              links: createdProject.links,
              images: createdProject.images,
              createdAt: createdProject.createdAt,
            },
            ...projects,
          ]
          return { slug: createdProject.slug }
        },
        uploadProjectImage,
      }}
    />,
  )

  await screen.getByRole('textbox', { name: 'Titel', exact: true }).fill('Image Warning Project')
  await screen.getByRole('textbox', { name: 'Beschreibung', exact: true }).fill(
    'A project with an image upload that fails after the main project has already been created successfully.',
  )
  const imageInput = screen.getByLabelText('Projektbilder').element() as HTMLInputElement
  const imageFiles = new DataTransfer()
  imageFiles.items.add(
    new File([new Uint8Array([0x89, 0x50, 0x4e, 0x47])], 'project.png', { type: 'image/png' }),
  )
  Object.defineProperty(imageInput, 'files', {
    value: imageFiles.files,
    configurable: true,
  })
  imageInput.dispatchEvent(new Event('change', { bubbles: true }))
  await expect.element(screen.getByText('project.png')).toBeVisible()
  await screen.getByLabelText('Deine Rolle in diesem Projekt').fill('Founder + Media Ops')
  await screen.getByLabelText('Wozu du dich persönlich verpflichtest').fill(
    'I will keep the project details updated and retry image uploads when needed.',
  )
  await screen.getByLabelText('Titel für Rolle 1').fill('Frontend Engineer')
  await screen.getByLabelText('Commitment für Rolle 1').fill('Help polish the project presentation flow.')
  await screen.getByRole('button', { name: 'Projekt erstellen' }).nth(1).click()

  await expect.element(screen.getByRole('heading', { name: 'Image Warning Project' })).toBeVisible()
  await expect.element(screen.getByText(
    'Projekt erstellt, aber einige Bilder konnten nicht gespeichert werden. Du kannst das Projekt bearbeiten und es erneut versuchen.',
  )).toBeVisible()
  expect(uploadProjectImage).toHaveBeenCalledOnce()
})
