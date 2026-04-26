import type { Project, ProjectFeedResponse } from './types'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api'

export async function loadProjects(): Promise<Project[]> {
  const response = await fetch(`${API_BASE_URL}/projects`)

  if (!response.ok) {
    throw new Error(`Failed to load projects (${response.status})`)
  }

  const payload = (await response.json()) as ProjectFeedResponse
  return payload.projects
}
