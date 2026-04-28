import { expect, test } from '@playwright/test'

const bootstrapInvite = 'basu-bootstrap-invite-2026'
const bootstrapEmail = 'basuabhi92@gmail.com'
const bootstrapPassword = 'SuperSafe1'
const bootstrapDisplayName = 'Ab Basu'

test('renders the project feed and completes invite registration plus login', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByRole('heading', { name: 'Discover real projects looking for the right people.' })).toBeVisible()
  await expect(page.getByText('Solar For Neighbors')).toBeVisible()
  await expect(page.getByText('Neighborhood Tool Library')).toBeVisible()

  await page.goto(`/register?invite=${bootstrapInvite}`)

  await expect(page.getByRole('heading', { name: 'Join Mitbauen.' })).toBeVisible()
  await expect(page.getByLabel('Email')).toHaveValue(bootstrapEmail)
  await page.getByLabel('Display name').fill(bootstrapDisplayName)
  await page.getByLabel('Password').fill(bootstrapPassword)
  await page.getByRole('button', { name: 'Create account' }).click()

  await expect(page.getByText(`Welcome, ${bootstrapDisplayName}`)).toBeVisible()
  await expect(page.getByText('Your invite link is ready.')).toBeVisible()

  await page.getByRole('button', { name: 'Log out' }).click()
  await expect(page.getByRole('button', { name: 'Sign in', exact: true })).toBeVisible()

  await page.goto('/login')
  await page.getByLabel('Email').fill(bootstrapEmail)
  await page.getByLabel('Password').fill(bootstrapPassword)
  await page.locator('.auth-form').getByRole('button', { name: 'Sign in', exact: true }).click()

  await expect(page.getByText(`Welcome, ${bootstrapDisplayName}`)).toBeVisible()
})
