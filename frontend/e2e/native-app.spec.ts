import { expect, test } from '@playwright/test'

const sharedInvite = 'test-open-invite'
const memberEmail = 'builder.one@example.test'
const memberPassword = 'SuperSafe1'
const memberDisplayName = 'Alex Builder'

test('renders the project feed and completes invite registration plus login', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByRole('heading', { name: 'Discover real projects looking for the right people.' })).toBeVisible()
  await expect(page.getByText('Solar For Neighbors')).toBeVisible()
  await expect(page.getByText('Neighborhood Tool Library')).toBeVisible()

  await page.goto(`/register?invite=${sharedInvite}`)

  await expect(page.getByRole('heading', { name: 'Join Mitbauen.' })).toBeVisible()
  await page.getByLabel('Email').fill(memberEmail)
  await page.getByLabel('Display name').fill(memberDisplayName)
  await page.getByLabel('Password').fill(memberPassword)
  await page.getByRole('button', { name: 'Create account' }).click()

  await expect(page.getByText(`Welcome, ${memberDisplayName}`)).toBeVisible()

  await page.getByRole('button', { name: 'Log out' }).click()
  await expect(page.locator('header').getByRole('button', { name: 'Sign in', exact: true })).toBeVisible()

  await page.goto('/login')
  await page.getByLabel('Email').fill(memberEmail)
  await page.getByLabel('Password').fill(memberPassword)
  await page.locator('.auth-form').getByRole('button', { name: 'Sign in', exact: true }).click()

  await expect(page.getByText(`Welcome, ${memberDisplayName}`)).toBeVisible()
})
