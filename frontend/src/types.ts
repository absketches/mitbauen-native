export type FounderInfo = {
  name: string
  role: string
  commitment: string
}

export type OpenRole = {
  title: string
  commitment: string
}

export type Project = {
  id: number
  slug: string
  title: string
  description: string
  status: 'active' | 'completed' | 'dormant'
  founder: FounderInfo
  openRoles: OpenRole[]
  createdAt: string
}

export type ProjectFeedResponse = {
  projects: Project[]
}

export type ProjectDetails = Project & {
  ownerUserId: number
  updatedAt: string
}

export type ProjectDetailsResponse = {
  project: ProjectDetails
}

export type SessionUser = {
  id: number
  displayName: string
  email: string
}

export type SessionResponse = {
  authenticated: boolean
  user?: SessionUser
}

export type InviteValidationResponse = {
  valid: boolean
}

export type RegisterPayload = {
  inviteToken: string
  email: string
  displayName: string
  password: string
}

export type LoginPayload = {
  email: string
  password: string
}

export type ProjectPayload = {
  title: string
  description: string
  founderRole: string
  founderCommitment: string
  openRoles: OpenRole[]
}

export type ProjectMutationResponse = {
  slug: string
}
