#!/usr/bin/env node

// Copies the Structurizr themes bundled with Kroki from the structurizr/structurizr
// repository and inlines their icons as data URIs so that conversions do not depend
// on network access at runtime.
//
// Only the two latest versions of each provider theme are bundled (to keep the size
// of the server reasonable). Other theme versions fall back to the latest bundled
// version of the provider (see Structurizr.java).
//
// The reference list of themes is available at:
// - https://github.com/structurizr/structurizr/tree/main/structurizr-themes (sources)
// - https://playground.structurizr.com/static/themes/<name>/theme.json (published, name without day)
// - https://static.structurizr.com/themes/<name-with-day>/theme.json (legacy CDN, name with day)

import { promises as fs } from 'node:fs'
import * as url from 'node:url'
import os from 'node:os'
import ospath from 'node:path'
import { execFileSync } from 'node:child_process'

const __dirname = url.fileURLToPath(new URL('.', import.meta.url))
const rootDir = ospath.join(__dirname, '..', '..')
const themesDir = ospath.join(rootDir, 'server', 'src', 'main', 'resources', 'structurizr')

const structurizrRepository = 'https://github.com/structurizr/structurizr.git'

// The two latest versions of each provider theme.
// The "default" theme (no icon) is maintained by Kroki and is not managed by this script.
const themeNames = [
  'amazon-web-services-2023.01',
  'amazon-web-services-2025.07',
  'google-cloud-platform-v1.5',
  'google-cloud-platform-2025.09',
  'kubernetes',
  'microsoft-azure-2024.07',
  'microsoft-azure-2025.11',
  'oracle-cloud-infrastructure-2021.04',
  'oracle-cloud-infrastructure-2023.04'
]

async function inlineIcons(theme, themeSourceDir) {
  let count = 0
  for (const element of theme.elements || []) {
    if (element.icon && !element.icon.startsWith('data:')) {
      const icon = await fs.readFile(ospath.join(themeSourceDir, element.icon))
      element.icon = `data:image/png;base64,${icon.toString('base64')}`
      count++
    }
  }
  return count
}

let cloneDir
try {
  cloneDir = await fs.mkdtemp(ospath.join(os.tmpdir(), 'structurizr-themes-'))
  execFileSync('git', ['clone', '--quiet', '--depth', '1', '--filter=blob:none', '--sparse', structurizrRepository, cloneDir], { stdio: 'inherit' })
  execFileSync('git', ['-C', cloneDir, 'sparse-checkout', 'set', 'structurizr-themes'], { stdio: 'inherit' })
  for (const themeName of themeNames) {
    const themeSourceDir = ospath.join(cloneDir, 'structurizr-themes', themeName)
    const theme = JSON.parse(await fs.readFile(ospath.join(themeSourceDir, 'theme.json'), 'utf8'))
    const iconCount = await inlineIcons(theme, themeSourceDir)
    const content = `${JSON.stringify(theme, null, 2)}\n`
    await fs.writeFile(ospath.join(themesDir, `${themeName}.json`), content, 'utf8')
    console.log(`${themeName}: ${iconCount} icons inlined (${(content.length / 1024 / 1024).toFixed(2)} MiB)`)
  }
} catch (err) {
  console.error(err)
  process.exit(1)
} finally {
  if (cloneDir) {
    await fs.rm(cloneDir, { recursive: true, force: true })
  }
}