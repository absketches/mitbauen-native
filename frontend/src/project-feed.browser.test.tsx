/// <reference types="@vitest/browser/matchers" />
/// <reference types="@vitest/browser/providers/playwright" />

import { beforeEach, expect, test, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import App from './App'
import { ApiError } from './api'
import type {
  InviteValidationResponse,
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
    description:
      'A cooperative toolkit for apartment blocks to coordinate balcony solar installs, document rooftop constraints, and help neighbors compare realistic energy savings before they commit to a shared rollout.',
    status: 'active',
    founder: {
      publicId: 'usr_avery_bloom_01',
      name: 'Avery Bloom',
      role: 'Founder + Product',
      commitment: '10 hrs/week',
    },
    openRoles: [
      { title: 'Android Engineer', commitment: '6 hrs/week' },
      { title: 'Community Researcher', commitment: '4 hrs/week' },
    ],
    createdAt: '2026-04-22T09:00:00Z',
  },
  {
    id: 2,
    slug: 'neighborhood-tool-library',
    title: 'Neighborhood Tool Library',
    description:
      'A simple way for neighbors to share tools, coordinate booking windows, and capture repair know-how so the same drill, ladder, or sewing machine can stay useful across an entire street.',
    status: 'active',
    founder: {
      publicId: 'usr_nora_patel_01',
      name: 'Nora Patel',
      role: 'Founder + Ops',
      commitment: '8 hrs/week',
    },
    openRoles: [
      { title: 'Frontend Engineer', commitment: '5 hrs/week' },
      { title: 'Partnerships Lead', commitment: '3 hrs/week' },
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

beforeEach(() => {
  window.localStorage.clear()
})

test('renders the public project feed in browser mode', async () => {
  window.history.pushState({}, '', '/')

  const screen = await render(
    <App
      api={{
        loadProjects: async () => [],
        loadSession: async (): Promise<SessionResponse> => ({ authenticated: false }),
      }}
    />,
  )

  await expect.element(screen.getByText('Finde Projekte mit konkretem Bedarf.')).toBeVisible()
  await expect.element(screen.getByText('Noch keine Projekte.')).toBeVisible()
  await expect.element(screen.getByText('Hier erscheinen Projekte, sobald das erste veröffentlicht ist.')).toBeVisible()
  await expect.element(screen.getByText('Alle Plattformdaten werden in Deutschland gespeichert und gemäß EU-Datenschutzstandards behandelt.')).toBeVisible()
  await expect.element(screen.getByRole('button', { name: 'Anmelden' })).toBeVisible()
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
  await expect.element(screen.getByRole('button', { name: 'Sign in' })).toBeVisible()
  expect(document.documentElement.lang).toBe('en')
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
        loadSession: async (): Promise<SessionResponse> => ({ authenticated: false }),
      }}
    />,
  )

  await screen.getByRole('button', { name: 'Avery Bloom' }).click()

  await expect.element(screen.getByRole('heading', { name: 'Avery Bloom' })).toBeVisible()
  await expect.element(screen.getByText('Helping neighbors move ideas into real, founder-led experiments.')).toBeVisible()
})

test('shows localized copy for deleted public profiles', async () => {
  window.history.pushState({}, '', '/')

  const screen = await render(
    <App
      api={{
        loadProjects: async () => baseProjects,
        loadPublicProfile: async () => {
          throw new ApiError('Request failed (410)', 410, 'USER_DELETED')
        },
        loadSession: async (): Promise<SessionResponse> => ({ authenticated: false }),
      }}
    />,
  )

  await screen.getByRole('button', { name: 'Avery Bloom' }).click()

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
  await screen.getByLabelText('Beschreibung').fill('Too short to pass.')
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

test('creates a project, lands on detail, and returns to a highlighted feed card', async () => {
  window.history.pushState({}, '', '/projects/new')

  let createdProject: ProjectDetails | null = null
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
          createdProject = {
            id: 99,
            canManage: true,
            slug: 'circular-kitchen-atlas',
            title: payload.title,
            description: payload.description,
            status: 'active',
            founder: {
              publicId: 'usr_alex_builder_01',
              name: 'Alex Builder',
              role: payload.founderRole,
              commitment: payload.founderCommitment,
            },
            openRoles: payload.openRoles,
            createdAt: '2026-04-29T10:00:00Z',
            updatedAt: '2026-04-29T10:00:00Z',
          }
          projects = [
            {
              id: createdProject.id,
              slug: createdProject.slug,
              title: createdProject.title,
              description: createdProject.description,
              status: createdProject.status,
              founder: createdProject.founder,
              openRoles: createdProject.openRoles,
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
  await screen.getByLabelText('Beschreibung').fill(
    'A living guide for neighborhood kitchens that want to map surplus food, shared prep capacity, and the fastest path from extra ingredients to community meals.',
  )
  await screen.getByLabelText('Deine Rolle in diesem Projekt').fill('Founder + Community Ops')
  await screen.getByLabelText('Wozu du dich persönlich verpflichtest').fill(
    'I am already running the pilot dinners, documenting learnings, and coordinating the volunteer operations myself.',
  )
  await screen.getByLabelText('Titel für Rolle 1').fill('Frontend Engineer')
  await screen.getByLabelText('Commitment für Rolle 1').fill('Build the first contributor-facing workflows.')
  await screen.getByRole('button', { name: 'Projekt erstellen' }).nth(1).click()

  await expect.element(screen.getByRole('heading', { name: 'Circular Kitchen Atlas' })).toBeVisible()
  await expect.element(screen.getByText('Projekt erstellt.')).toBeVisible()

  await screen.getByRole('button', { name: 'Zurück zu den Projekten' }).click()

  await expect.element(screen.getByText('Projekt veröffentlicht.')).toBeVisible()
  await expect.element(screen.getByTestId('project-circular-kitchen-atlas')).toBeVisible()
})
