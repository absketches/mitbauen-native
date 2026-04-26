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
  summary: string
  status: 'active' | 'completed' | 'dormant'
  founder: FounderInfo
  openRoles: OpenRole[]
  createdAt: string
}

export type ProjectFeedResponse = {
  projects: Project[]
}
