import { expect, test } from '@playwright/test'

const sharedInvite = 'test-open-invite'
const memberEmail = 'builder.one@example.test'
const memberPassword = 'SuperSafe1'
const memberDisplayName = 'Alex Builder'

test('renders the project feed and completes invite registration plus login', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByRole('heading', { name: 'Discover real projects looking for the right people.' })).toBeVisible()
  await expect(page.getByText('No projects yet.')).toBeVisible()

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

  await page.locator('header').getByRole('button', { name: 'Create project', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Post a project that shows real momentum.' })).toBeVisible()

  await page.getByLabel('Project title').fill('Circular Kitchen Atlas')
  await page.getByLabel('Project description').fill(
    'A living guide for neighborhood kitchens that want to map surplus food, shared prep capacity, and the fastest path from extra ingredients to community meals.',
  )
  await page.getByLabel('Your role in this project').fill('Founder + Community Ops')
  await page.getByLabel('What you are personally committing').fill(
    'I am already running the pilot dinners, documenting learnings, and coordinating the volunteer operations myself.',
  )
  await page.getByLabel('Role title 1').fill('Frontend Engineer')
  await page.getByLabel('Role commitment 1').fill('Build the first contributor-facing workflows.')
  await page.getByRole('button', { name: 'Create project' }).click()

  await expect(page.getByRole('heading', { name: 'Circular Kitchen Atlas' })).toBeVisible()
  await expect(page.getByText('Project created. It is ready for people to discover, and the feed can highlight it for you.')).toBeVisible()

  await page.getByRole('button', { name: 'Back to feed' }).click()
  await expect(page.getByText('Your new project is live in the feed.')).toBeVisible()
  await expect(page.getByTestId('project-circular-kitchen-atlas')).toBeVisible()
})
