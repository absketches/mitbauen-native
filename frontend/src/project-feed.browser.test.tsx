/// <reference types="@vitest/browser/matchers" />
/// <reference types="@vitest/browser/providers/playwright" />

import { expect, test } from 'vitest'
import { render } from 'vitest-browser-react'
import App from './App'
import type { Project } from './types'

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
  const screen = await render(<App fetchProjects={async () => projects} />)

  await expect.element(screen.getByText('Projects that already have founder energy behind them.')).toBeVisible()
  await expect.element(screen.getByText('Solar For Neighbors')).toBeVisible()
  await expect.element(screen.getByText('Neighborhood Tool Library')).toBeVisible()
  await expect.element(screen.getByText('Founder + Product')).toBeVisible()
  await expect.element(screen.getByText('Android Engineer')).toBeVisible()
})
