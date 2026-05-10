import { execFileSync } from 'node:child_process'
import { expect, test } from '@playwright/test'

const sharedInvite = 'test-open-invite'
const memberEmail = 'builder.one@example.test'
const memberPassword = 'SuperSafe1'
const memberDisplayName = 'Alex Builder'

test('renders the project feed and completes invite registration plus login', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByRole('heading', { name: 'Finde Projekte mit konkretem Bedarf.' })).toBeVisible()
  await expect(page.getByText('Builders Inside')).toBeVisible()
  await expect(page.getByText('Lokale Projekte liegen hinter der Mitgliedertür.')).toBeVisible()

  await page.goto(`/register?invite=${sharedInvite}`)

  await expect(page.getByRole('heading', { name: 'Konto erstellen.' })).toBeVisible()
  await page.getByLabel('E-Mail', { exact: true }).fill(memberEmail)
  await page.getByLabel('Anzeigename').fill(memberDisplayName)
  await page.getByLabel('Passwort').fill(memberPassword)
  await page.getByRole('button', { name: 'Konto erstellen' }).click()

  await expect(page.getByText(`Willkommen, ${memberDisplayName}`)).toBeVisible()
  markEmailVerified(memberEmail)

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

function markEmailVerified(email: string) {
  const jdbcUrl = process.env.jdbc_database_url
  const jdbcUser = process.env.jdbc_database_user
  if (!jdbcUrl || !jdbcUser) {
    throw new Error('Missing JDBC connection details for native E2E email verification')
  }

  const sql = "update users set email_verified_at = current_timestamp where email = '" + escapeSql(email) + "'"

  if (commandAvailable('psql')) {
    execFileSync(
      'psql',
      [jdbcUrl.replace(/^jdbc:/, ''), '-U', jdbcUser, '-v', 'ON_ERROR_STOP=1', '-c', sql],
      {
        env: {
          ...process.env,
          PGPASSWORD: process.env.jdbc_database_password ?? '',
        },
        stdio: 'ignore',
      },
    )
    return
  }

  const dockerContainerId = findPostgresContainerId(jdbcUrl)
  execFileSync(
    'docker',
    [
      'exec',
      '-e',
      `PGPASSWORD=${process.env.jdbc_database_password ?? ''}`,
      dockerContainerId,
      'psql',
      '-U',
      jdbcUser,
      '-d',
      databaseName(jdbcUrl),
      '-v',
      'ON_ERROR_STOP=1',
      '-c',
      sql,
    ],
    {
      stdio: 'ignore',
    },
  )
}

function commandAvailable(command: string) {
  try {
    execFileSync(command, ['--version'], { stdio: 'ignore' })
    return true
  } catch {
    return false
  }
}

function databaseName(jdbcUrl: string) {
  const withoutPrefix = jdbcUrl.replace(/^jdbc:postgresql:\/\//, '')
  const slashIndex = withoutPrefix.indexOf('/')
  if (slashIndex < 0) {
    throw new Error(`Unsupported jdbc_database_url for native E2E: ${jdbcUrl}`)
  }
  return withoutPrefix.slice(slashIndex + 1).split('?')[0]
}

function postgresPort(jdbcUrl: string) {
  const withoutPrefix = jdbcUrl.replace(/^jdbc:postgresql:\/\//, '')
  const hostPort = withoutPrefix.split('/')[0]
  const colonIndex = hostPort.lastIndexOf(':')
  if (colonIndex < 0) {
    return '5432'
  }
  return hostPort.slice(colonIndex + 1)
}

function findPostgresContainerId(jdbcUrl: string) {
  const containerId = execFileSync(
    'docker',
    ['ps', '--filter', `publish=${postgresPort(jdbcUrl)}`, '--format', '{{.ID}}'],
    { encoding: 'utf8' },
  )
    .trim()
    .split('\n')[0]

  if (!containerId) {
    throw new Error(`Unable to find a running Postgres container for ${jdbcUrl}`)
  }
  return containerId
}

function escapeSql(value: string) {
  return value.replaceAll("'", "''")
}
