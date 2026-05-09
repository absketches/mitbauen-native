import type {
  InviteValidationResponse,
  LoginPayload,
  NotificationItem,
  NotificationsResponse,
  PasswordResetConfirmPayload,
  PasswordResetConfirmResponse,
  PasswordResetRequestPayload,
  PasswordResetRequestResponse,
  PublicUserProfile,
  PublicUserProfileResponse,
  Project,
  ProjectComment,
  ProjectCommentPayload,
  ProjectCommentResponse,
  ProjectCommentsReadResponse,
  ProjectCommentsResponse,
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
  VerificationConfirmResponse,
  VerificationEmailRequestResponse,
} from './types'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api'

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code?: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

export async function loadProjects(): Promise<Project[]> {
  const payload = await requestJson<ProjectFeedResponse>(`${API_BASE_URL}/projects`)
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

export async function loadPublicProfile(publicId: string): Promise<PublicUserProfile> {
  const payload = await requestJson<PublicUserProfileResponse>(`${API_BASE_URL}/users/${encodeURIComponent(publicId)}`)
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
  })
}

export async function requestEmailVerification(): Promise<VerificationEmailRequestResponse> {
  return requestJson<VerificationEmailRequestResponse>(`${API_BASE_URL}/auth/verify-email/request`, {
    method: 'POST',
  })
}

export async function confirmEmailVerification(token: string): Promise<VerificationConfirmResponse> {
  return requestJson<VerificationConfirmResponse>(`${API_BASE_URL}/auth/verify-email/confirm`, {
    method: 'POST',
    body: JSON.stringify({ token }),
  })
}

export async function requestPasswordReset(payload: PasswordResetRequestPayload): Promise<PasswordResetRequestResponse> {
  return requestJson<PasswordResetRequestResponse>(`${API_BASE_URL}/auth/password-reset/request`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export async function confirmPasswordReset(payload: PasswordResetConfirmPayload): Promise<PasswordResetConfirmResponse> {
  return requestJson<PasswordResetConfirmResponse>(`${API_BASE_URL}/auth/password-reset/confirm`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export async function updateProfile(payload: UserProfilePayload): Promise<UserProfile> {
  const response = await requestJson<UserProfileResponse>(`${API_BASE_URL}/profile`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
  return response.profile
}

export async function deleteAccount(): Promise<SessionResponse> {
  return requestJson<SessionResponse>(`${API_BASE_URL}/profile`, {
    method: 'DELETE',
  })
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

export async function loadProjectComments(slug: string): Promise<ProjectComment[]> {
  const payload = await requestJson<ProjectCommentsResponse>(`${API_BASE_URL}/projects/${encodeURIComponent(slug)}/comments`)
  return payload.comments
}

export async function createProjectComment(slug: string, payload: ProjectCommentPayload): Promise<ProjectComment> {
  const response = await requestJson<ProjectCommentResponse>(`${API_BASE_URL}/projects/${encodeURIComponent(slug)}/comments`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
  return response.comment
}

export async function markProjectCommentsRead(slug: string): Promise<ProjectCommentsReadResponse> {
  return requestJson<ProjectCommentsReadResponse>(`${API_BASE_URL}/projects/${encodeURIComponent(slug)}/comments/read`, {
    method: 'POST',
  })
}

export async function loadNotifications(): Promise<NotificationItem[]> {
  const payload = await requestJson<NotificationsResponse>(`${API_BASE_URL}/notifications`)
  return payload.notifications
}

async function requestJson<T>(input: string, init?: RequestInit): Promise<T> {
  const headers = init?.body
    ? {
        'Content-Type': 'application/json',
        ...(init.headers ?? {}),
      }
    : init?.headers
  const response = await fetch(input, {
    headers,
    ...init,
    credentials: 'include',
  })

  let payload: unknown = null
  if (response.status !== 204) {
    payload = await response.json()
  }

  if (!response.ok) {
    const code =
      typeof payload === 'object' && payload !== null && 'code' in payload && typeof payload.code === 'string'
        ? payload.code
        : undefined
    const message =
      typeof payload === 'object' && payload !== null && 'error' in payload && typeof payload.error === 'string'
        ? payload.error
        : code ?? `Request failed (${response.status})`
    throw new ApiError(message, response.status, code)
  }

  return payload as T
}
