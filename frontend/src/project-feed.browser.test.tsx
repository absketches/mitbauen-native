/// <reference types="@vitest/browser/matchers" />
/// <reference types="@vitest/browser/providers/playwright" />

import { expect, test, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import App from './App'
import type {
  InviteValidationResponse,
  Project,
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

  await expect.element(screen.getByText('Discover real projects looking for the right people.')).toBeVisible()
  await expect.element(screen.getByText('No projects yet.')).toBeVisible()
  await expect.element(screen.getByText('The public feed will show founder commitment and open roles here once the first project is posted.')).toBeVisible()
  await expect.element(screen.getByRole('button', { name: 'Sign in' })).toBeVisible()
  expect(document.body.textContent).not.toContain('Join Mitbauen through an invite.')
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

  await expect.element(screen.getByText('Join Mitbauen.')).toBeVisible()
  await expect.element(screen.getByRole('textbox', { name: 'Email' })).toHaveValue('')
  await expect.element(screen.getByRole('button', { name: 'Create account' })).toBeVisible()
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
          user: { id: 42, displayName: 'Alex Builder', email: 'alex@example.test' },
        }),
        createProject: createProjectMock,
      }}
    />,
  )

  await screen.getByLabelText('Project title').fill('Tiny')
  await screen.getByLabelText('Project description').fill('Too short to pass.')
  await screen.getByLabelText('Your role in this project').fill('Go')
  await screen.getByLabelText('What you are personally committing').fill('Too short')
  await screen.getByLabelText('Role title 1').fill('No')
  await screen.getByLabelText('Role commitment 1').fill('No')
  await screen.getByRole('button', { name: 'Create project' }).nth(1).click()

  await expect.element(screen.getByText('Use a project title between 5 and 120 characters.')).toBeVisible()
  await expect.element(screen.getByText('Use a project description between 40 and 1024 characters.')).toBeVisible()
  await expect.element(screen.getByText('Use a founder role between 3 and 80 characters.')).toBeVisible()
  await expect.element(screen.getByText('Use a founder commitment between 10 and 280 characters.')).toBeVisible()
  await expect.element(screen.getByText('Use a role title between 3 and 80 characters.')).toBeVisible()
  await expect.element(screen.getByText('Use a role commitment between 3 and 80 characters.')).toBeVisible()
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
        loadProject: async (slug: string) => {
          if (createdProject?.slug === slug) {
            return createdProject
          }
          throw new Error('Project not found.')
        },
        loadSession: async (): Promise<SessionResponse> => ({
          authenticated: true,
          user: { id: 42, displayName: 'Alex Builder', email: 'alex@example.test' },
        }),
        createProject: async (payload: ProjectPayload) => {
          createdProject = {
            id: 99,
            ownerUserId: 42,
            slug: 'circular-kitchen-atlas',
            title: payload.title,
            description: payload.description,
            status: 'active',
            founder: {
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

  await screen.getByLabelText('Project title').fill('Circular Kitchen Atlas')
  await screen.getByLabelText('Project description').fill(
    'A living guide for neighborhood kitchens that want to map surplus food, shared prep capacity, and the fastest path from extra ingredients to community meals.',
  )
  await screen.getByLabelText('Your role in this project').fill('Founder + Community Ops')
  await screen.getByLabelText('What you are personally committing').fill(
    'I am already running the pilot dinners, documenting learnings, and coordinating the volunteer operations myself.',
  )
  await screen.getByLabelText('Role title 1').fill('Frontend Engineer')
  await screen.getByLabelText('Role commitment 1').fill('Build the first contributor-facing workflows.')
  await screen.getByRole('button', { name: 'Create project' }).nth(1).click()

  await expect.element(screen.getByRole('heading', { name: 'Circular Kitchen Atlas' })).toBeVisible()
  await expect.element(screen.getByText('Project created. It is ready for people to discover, and the feed can highlight it for you.')).toBeVisible()

  await screen.getByRole('button', { name: 'Back to feed' }).click()

  await expect.element(screen.getByText('Your new project is live in the feed.')).toBeVisible()
  await expect.element(screen.getByTestId('project-circular-kitchen-atlas')).toBeVisible()
})
