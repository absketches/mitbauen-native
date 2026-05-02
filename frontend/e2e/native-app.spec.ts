import { expect, test } from '@playwright/test'

const sharedInvite = 'test-open-invite'
const memberEmail = 'builder.one@example.test'
const memberPassword = 'SuperSafe1'
const memberDisplayName = 'Alex Builder'

test('renders the project feed and completes invite registration plus login', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByRole('heading', { name: 'Finde Projekte mit konkretem Bedarf.' })).toBeVisible()
  await expect(page.getByText('Noch keine Projekte.')).toBeVisible()

  await page.goto(`/register?invite=${sharedInvite}`)

  await expect(page.getByRole('heading', { name: 'Konto erstellen.' })).toBeVisible()
  await page.getByLabel('E-Mail', { exact: true }).fill(memberEmail)
  await page.getByLabel('Anzeigename').fill(memberDisplayName)
  await page.getByLabel('Passwort').fill(memberPassword)
  await page.getByRole('button', { name: 'Konto erstellen' }).click()

  await expect(page.getByText(`Willkommen, ${memberDisplayName}`)).toBeVisible()

  await page.getByRole('button', { name: 'Abmelden' }).click()
  await expect(page.locator('header').getByRole('button', { name: 'Anmelden', exact: true })).toBeVisible()

  await page.goto('/login')
  await page.getByLabel('E-Mail', { exact: true }).fill(memberEmail)
  await page.getByLabel('Passwort').fill(memberPassword)
  await page.locator('.auth-form').getByRole('button', { name: 'Anmelden', exact: true }).click()

  await expect(page.getByText(`Willkommen, ${memberDisplayName}`)).toBeVisible()

  await page.locator('header').getByRole('button', { name: 'Projekt erstellen', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Projekt erstellen.' })).toBeVisible()

  await page.getByLabel('Titel', { exact: true }).fill('Circular Kitchen Atlas')
  await page.getByLabel('Beschreibung').fill(
    'A living guide for neighborhood kitchens that want to map surplus food, shared prep capacity, and the fastest path from extra ingredients to community meals.',
  )
  await page.getByLabel('Deine Rolle in diesem Projekt').fill('Founder + Community Ops')
  await page.getByLabel('Wozu du dich persönlich verpflichtest').fill(
    'I am already running the pilot dinners, documenting learnings, and coordinating the volunteer operations myself.',
  )
  await page.getByLabel('Titel für Rolle 1').fill('Frontend Engineer')
  await page.getByLabel('Commitment für Rolle 1').fill('Build the first contributor-facing workflows.')
  await page.locator('form').getByRole('button', { name: 'Projekt erstellen', exact: true }).click()

  await expect(page.getByRole('heading', { name: 'Circular Kitchen Atlas' })).toBeVisible()
  await expect(page.getByText('Projekt erstellt.')).toBeVisible()

  await page.getByRole('button', { name: 'Zurück zu den Projekten' }).click()
  await expect(page.getByText('Projekt veröffentlicht.')).toBeVisible()
  await expect(page.getByTestId('project-circular-kitchen-atlas')).toBeVisible()
})
