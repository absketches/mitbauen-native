export type FounderInfo = {
  publicId: string
  name: string
  role: string
  commitment: string
}

export type OpenRole = {
  id?: number
  title: string
  commitment: string
}

export type ProjectLink = {
  label: string
  url: string
}

export type ProjectImage = {
  id: number
  url: string
  contentType: string
  sizeBytes: number
  altText: string
  createdAt: string
}

export type ProjectDescriptions = {
  de: string | null
  en: string | null
}

export type Project = {
  id: number
  slug: string
  title: string
  descriptions: ProjectDescriptions
  status: 'active' | 'completed' | 'dormant'
  founder: FounderInfo
  openRoles: OpenRole[]
  links?: ProjectLink[]
  images?: ProjectImage[]
  createdAt: string
}

export type ProjectFeedResponse = {
  projects: Project[]
}

export type JobListing = {
  id: string
  roleId: number
  projectSlug: string
  projectTitle: string
  roleTitle: string
  roleCommitment: string
}

export type JobsResponse = {
  jobs: JobListing[]
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

export type PasswordResetRequestResponse = {
  requested: boolean
}

export type PasswordResetConfirmResponse = {
  reset: boolean
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

export type PasswordResetRequestPayload = {
  email: string
}

export type PasswordResetConfirmPayload = {
  token: string
  password: string
}

export type ProjectPayload = {
  title: string
  descriptions: ProjectDescriptions
  founderRole: string
  founderCommitment: string
  openRoles: OpenRole[]
  links: ProjectLink[]
}

export type ProjectImageChanges = {
  newImages: File[]
  removedImageIds: number[]
}

export type ProjectMutationResponse = {
  slug: string
}

export type UserProfilePayload = {
  displayName: string
  bio: string
  emailPublic: boolean
}

export type ProjectComment = {
  id: number
  body: string
  authorPublicId: string
  authorDisplayName: string
  createdAt: string
}

export type ProjectCommentsResponse = {
  comments: ProjectComment[]
}

export type ProjectCommentResponse = {
  comment: ProjectComment
}

export type ProjectCommentPayload = {
  body: string
}

export type JobApplicationPayload = {
  roleId: number
  fit: string
  availability: string
}

export type JobApplicationResponse = {
  sent: boolean
}

export type ProjectCommentsReadResponse = {
  read: boolean
}

export type NotificationItem = {
  id: string
  type: 'project_comment'
  projectSlug: string
  projectTitle: string
  actorName: string
  latestBody: string
  latestAt: string
  unreadCount: number
}

export type NotificationsResponse = {
  notifications: NotificationItem[]
}
