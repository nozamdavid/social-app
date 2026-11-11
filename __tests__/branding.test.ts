import {execFileSync} from 'node:child_process'
import {readFileSync} from 'node:fs'
import {resolve} from 'node:path'

import {themes} from '#/alf/themes'
import brand from '#/../assets/branding/caelum/brand.json'
import brandWebHtml from '#/../scripts/branding/brandWebHtml'

jest.mock('#/env', () => ({IS_BLUESKY_PLUS: true}))

it('keeps all generated native, vector and raster assets in sync with the masters', () => {
  expect(
    execFileSync(
      process.execPath,
      ['scripts/generateBranding.mjs', '--check'],
      {
        cwd: resolve(__dirname, '..'),
        encoding: 'utf8',
      },
    ),
  ).toContain('Brand assets are up to date')
})

it('uses the central brand colors for all default theme modes', () => {
  for (const name of ['light', 'dim', 'dark'] as const) {
    expect(themes[name].palette.primary_500).toBe(brand.colors.primary)
    expect(themes[name].onAccent).toBe(brand.colors.onPrimary)
  }
  expect(themes.light.atoms.bg.backgroundColor).toBe(brand.colors.light)
  expect(themes.dim.atoms.bg.backgroundColor).toBe(brand.colors.dark)
  expect(themes.dark.atoms.bg.backgroundColor).toBe(brand.colors.ink)
})

it('brands the exported web splash while preserving Bluesky service references', () => {
  const html = readFileSync(resolve(__dirname, '../public/index.html'), 'utf8')
  const mark = readFileSync(
    resolve(__dirname, '../assets/branding/caelum/generated/mark.svg'),
    'utf8',
  )
  const result = brandWebHtml(html, brand, mark)
  expect(result).toContain(mark)
  expect(result).toContain(`Enable JavaScript to use ${brand.name}.`)
  expect(result).toContain('https://bsky.social')
  expect(result).not.toContain('M13.873')
})
