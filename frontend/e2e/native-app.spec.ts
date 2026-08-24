import { execFileSync } from 'node:child_process'
import { createHash } from 'node:crypto'
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
  await page.getByRole('textbox', { name: 'Beschreibung', exact: true }).fill(
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

test('renders seeded translated project descriptions with the disclaimer', async ({ page }) => {
  const viewerEmail = `translated-viewer-${Date.now()}@example.test`
  const viewerPassword = 'SuperSafe1'
  const viewerDisplayName = 'Translated Viewer'
  const englishDescription =
    'A field-tested map for repair cafes that helps volunteers route broken appliances, reusable parts, and specialist skills to the right neighborhood workbench before the next repair day.'
  const germanTranslation =
    'Eine vor Ort getestete Karte für Reparaturcafes, die Freiwilligen hilft, defekte Geräte, wiederverwendbare Teile und Spezialwissen vor dem nächsten Reparaturtag an die passende Werkbank im Viertel zu bringen.'

  seedTranslatedProject({
    slug: 'translated-repair-map',
    title: 'Translated Repair Map',
    englishDescription,
    germanTranslation,
  })

  await page.goto(`/register?invite=${sharedInvite}`)
  await page.getByLabel('E-Mail', { exact: true }).fill(viewerEmail)
  await page.getByLabel('Anzeigename').fill(viewerDisplayName)
  await page.getByLabel('Passwort').fill(viewerPassword)
  await page.getByRole('button', { name: 'Konto erstellen' }).click()
  await expect(page.getByText(`Willkommen, ${viewerDisplayName}`)).toBeVisible()
  markEmailVerified(viewerEmail)

  await page.goto('/')
  await expect(page.getByTestId('project-translated-repair-map')).toBeVisible()
  await expect(page.getByText('Eine vor Ort getestete Karte für Reparaturcafes')).toBeVisible()
  await expect(page.getByText('Mit einem Tool übersetzt. Diese Version wurde nicht von der erstellenden Person bereitgestellt.')).toBeVisible()

  await page.getByTestId('project-translated-repair-map').getByRole('button', { name: 'Projekt ansehen' }).click()
  await expect(page.getByRole('heading', { name: 'Translated Repair Map' })).toBeVisible()
  await expect(page.getByText(germanTranslation)).toBeVisible()
  await expect(page.getByText('Mit einem Tool übersetzt. Diese Version wurde nicht von der erstellenden Person bereitgestellt.')).toBeVisible()

  await page.getByRole('button', { name: 'EN', exact: true }).click()
  await expect(page.getByText(englishDescription)).toBeVisible()
  await expect(page.getByText(germanTranslation)).not.toBeVisible()
})

function markEmailVerified(email: string) {
  const sql = "update users set email_verified_at = current_timestamp where email = '" + escapeSql(email) + "'"
  executeSql(sql)
}

function seedTranslatedProject({
  slug,
  title,
  englishDescription,
  germanTranslation,
}: {
  slug: string
  title: string
  englishDescription: string
  germanTranslation: string
}) {
  const ownerEmail = `${slug}@example.test`
  const ownerPublicId = `usr_${slug.replaceAll('-', '_')}`
  const sourceTextHash = createHash('sha256').update(englishDescription, 'utf8').digest('hex')
  const sql = `
delete from projects where slug = '${escapeSql(slug)}';

insert into users (display_name, email, bio, is_email_public, public_id, email_verified_at)
values ('Translated Project Owner', '${escapeSql(ownerEmail)}', '', false, '${escapeSql(ownerPublicId)}', current_timestamp)
on conflict (email) do update
set display_name = excluded.display_name,
    bio = excluded.bio,
    is_email_public = excluded.is_email_public,
    public_id = excluded.public_id,
    email_verified_at = excluded.email_verified_at;

insert into projects (owner_user_id, slug, title, description_en, status, created_at, updated_at)
values (
  (select id from users where email = '${escapeSql(ownerEmail)}'),
  '${escapeSql(slug)}',
  '${escapeSql(title)}',
  '${escapeSql(englishDescription)}',
  'active',
  current_timestamp,
  current_timestamp
);

insert into project_roles (project_id, title, commitment, is_founder, is_open, sort_order)
values
  ((select id from projects where slug = '${escapeSql(slug)}'), 'Founder + Repair Ops', 'Maintains the repair routing data and coordinates volunteer workbench leads.', true, false, 0),
  ((select id from projects where slug = '${escapeSql(slug)}'), 'Repair Data Steward', 'Keep the repair map useful for volunteers.', false, true, 1);

insert into project_description_translations (
  project_id, source_language, target_language, source_text_hash, translated_text, provider, model, created_at
)
values (
  (select id from projects where slug = '${escapeSql(slug)}'),
  'en',
  'de',
  '${sourceTextHash}',
  '${escapeSql(germanTranslation)}',
  'e2e',
  'seeded',
  current_timestamp
);
`
  executeSql(sql)
}

function executeSql(sql: string) {
  const jdbcUrl = process.env.jdbc_database_url
  const jdbcUser = process.env.jdbc_database_user
  if (!jdbcUrl || !jdbcUser) {
    throw new Error('Missing JDBC connection details for native E2E database setup')
  }

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
