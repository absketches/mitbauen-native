import type {
  InviteValidationResponse,
  LoginPayload,
  Project,
  ProjectDetails,
  ProjectDetailsResponse,
  ProjectFeedResponse,
  ProjectMutationResponse,
  ProjectPayload,
  RegisterPayload,
  SessionResponse,
  UserProfile,
  UserProfilePayload,
  UserProfileResponse,
} from './types'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api'

export async function loadProjects(): Promise<Project[]> {
  const response = await fetch(`${API_BASE_URL}/projects`, {
    credentials: 'same-origin',
  })

  if (!response.ok) {
    throw new Error(`Failed to load projects (${response.status})`)
  }

  const payload = (await response.json()) as ProjectFeedResponse
  return payload.projects
}

export async function loadProject(slug: string): Promise<ProjectDetails> {
  const payload = await requestJson<ProjectDetailsResponse>(`${API_BASE_URL}/projects/${encodeURIComponent(slug)}`)
  return payload.project
}

export async function loadSession(): Promise<SessionResponse> {
  return requestJson<SessionResponse>(`${API_BASE_URL}/auth/session`)
}

export async function loadProfile(): Promise<UserProfile> {
  const payload = await requestJson<UserProfileResponse>(`${API_BASE_URL}/profile`)
  return payload.profile
}

export async function validateInvite(token: string): Promise<InviteValidationResponse> {
  return requestJson<InviteValidationResponse>(`${API_BASE_URL}/invites/validate?token=${encodeURIComponent(token)}`)
}

export async function registerUser(payload: RegisterPayload): Promise<SessionResponse> {
  return requestJson<SessionResponse>(`${API_BASE_URL}/auth/register`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export async function loginUser(payload: LoginPayload): Promise<SessionResponse> {
  return requestJson<SessionResponse>(`${API_BASE_URL}/auth/login`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export async function logoutUser(): Promise<void> {
  await requestJson<SessionResponse>(`${API_BASE_URL}/auth/logout`, {
    method: 'POST',
    body: JSON.stringify({}),
  })
}

export async function updateProfile(payload: UserProfilePayload): Promise<UserProfile> {
  const response = await requestJson<UserProfileResponse>(`${API_BASE_URL}/profile`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
  return response.profile
}

export async function createProject(payload: ProjectPayload): Promise<ProjectMutationResponse> {
  return requestJson<ProjectMutationResponse>(`${API_BASE_URL}/projects`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export async function updateProject(slug: string, payload: ProjectPayload): Promise<ProjectMutationResponse> {
  return requestJson<ProjectMutationResponse>(`${API_BASE_URL}/projects/${encodeURIComponent(slug)}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export async function deleteProject(slug: string): Promise<void> {
  await requestJson<null>(`${API_BASE_URL}/projects/${encodeURIComponent(slug)}`, {
    method: 'DELETE',
  })
}

async function requestJson<T>(input: string, init?: RequestInit): Promise<T> {
  const response = await fetch(input, {
    credentials: 'same-origin',
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers ?? {}),
    },
    ...init,
  })

  let payload: unknown = null
  if (response.status !== 204) {
    payload = await response.json()
  }

  if (!response.ok) {
    const message =
      typeof payload === 'object' && payload !== null && 'error' in payload && typeof payload.error === 'string'
        ? payload.error
        : `Request failed (${response.status})`
    throw new Error(message)
  }

  return payload as T
}
