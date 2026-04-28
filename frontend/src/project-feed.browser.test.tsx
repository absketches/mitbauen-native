/// <reference types="@vitest/browser/matchers" />
/// <reference types="@vitest/browser/providers/playwright" />

import { expect, test } from 'vitest'
import { render } from 'vitest-browser-react'
import App from './App'
import type { InviteValidationResponse, Project, SessionResponse } from './types'

const projects: Project[] = [
  {
    id: 1,
    slug: 'solar-for-neighbors',
    title: 'Solar For Neighbors',
    summary: 'A cooperative toolkit for apartment blocks to coordinate balcony solar installs.',
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
    summary: 'A simple way for neighbors to share tools, booking slots, and repair know-how.',
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
        loadProjects: async () => projects,
        loadSession: async (): Promise<SessionResponse> => ({ authenticated: false }),
      }}
    />,
  )

  await expect.element(screen.getByText('Discover real projects looking for the right people.')).toBeVisible()
  await expect.element(screen.getByText('Solar For Neighbors')).toBeVisible()
  await expect.element(screen.getByText('Neighborhood Tool Library')).toBeVisible()
  await expect.element(screen.getByText('Founder + Product')).toBeVisible()
  await expect.element(screen.getByText('Android Engineer')).toBeVisible()
  await expect.element(screen.getByRole('button', { name: 'Sign in' })).toBeVisible()
  expect(document.body.textContent).not.toContain('Join Mitbauen through an invite.')
})

test('renders the invite-only registration view for an open invite', async () => {
  window.history.pushState({}, '', '/register?invite=test-open-invite')

  const screen = await render(
    <App
      api={{
        loadProjects: async () => projects,
        loadSession: async (): Promise<SessionResponse> => ({ authenticated: false }),
        validateInvite: async (): Promise<InviteValidationResponse> => ({
          valid: true,
        }),
      }}
    />,
  )

  await expect.element(screen.getByText('Join Mitbauen.')).toBeVisible()
  await expect.element(screen.getByLabelText('Email')).toHaveValue('')
  await expect.element(screen.getByRole('button', { name: 'Create account' })).toBeVisible()
})
