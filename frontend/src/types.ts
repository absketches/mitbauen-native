export type FounderInfo = {
  publicId: string
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
  canManage: boolean
  updatedAt: string
}

export type ProjectDetailsResponse = {
  project: ProjectDetails
}

export type SessionUser = {
  displayName: string
  email: string
  emailVerified: boolean
}

export type UserProfile = {
  displayName: string
  bio: string
  email: string
  emailPublic: boolean
  emailVerified: boolean
}

export type PublicUserProfile = Pick<UserProfile, 'displayName' | 'bio' | 'email'>

export type SessionResponse = {
  authenticated: boolean
  user?: SessionUser
}

export type UserProfileResponse = {
  profile: UserProfile
}

export type PublicUserProfileResponse = {
  profile: PublicUserProfile
}

export type InviteValidationResponse = {
  valid: boolean
}

export type VerificationEmailRequestResponse = {
  sent: boolean
  alreadyVerified: boolean
}

export type VerificationConfirmResponse = {
  verified: boolean
}

export type RegisterPayload = {
  inviteToken: string
  email: string
  bio: string
  emailPublic: boolean
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

export type UserProfilePayload = {
  displayName: string
  bio: string
  emailPublic: boolean
}
