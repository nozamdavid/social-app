import {readFile, mkdir, writeFile} from 'node:fs/promises'
import path from 'node:path'
import {fileURLToPath} from 'node:url'
import sharp from 'sharp'
import {optimize} from 'svgo'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const source = path.join(root, 'assets/branding/caelum')
const output = path.join(source, 'generated')
const check = process.argv.includes('--check')
const brand = JSON.parse(
  await readFile(path.join(source, 'brand.json'), 'utf8'),
)
const stale = []

/** Read monochrome path artwork without copying its geometry into app code. */
async function artwork(name) {
  let viewBox
  const paths = []
  optimize(await readFile(path.join(source, `${name}.svg`), 'utf8'), {
    plugins: [
      'preset-default',
      'convertShapeToPath',
      {
        name: 'readBrandArtwork',
        fn: () => ({
          element: {
            enter(node) {
              if (node.name === 'svg') viewBox = node.attributes.viewBox
              if (node.attributes.transform) {
                throw new Error(
                  `${name}.svg: flatten transforms before exporting`,
                )
              }
              if (node.name === 'path') {
                if (!node.attributes.d)
                  throw new Error(`${name}.svg: empty path`)
                paths.push(node.attributes.d)
              } else if (!['svg', 'title', 'desc'].includes(node.name)) {
                throw new Error(`${name}.svg: convert ${node.name} to paths`)
              }
            },
          },
        }),
      },
    ],
  })
  if (!viewBox || !paths.length)
    throw new Error(`${name}.svg needs a viewBox and paths`)
  const box = viewBox.split(/[ ,]+/).map(Number)
  if (
    box.length !== 4 ||
    box.some(n => !Number.isFinite(n)) ||
    box[2] <= 0 ||
    box[3] <= 0
  ) {
    throw new Error(`${name}.svg: invalid viewBox`)
  }
  return {viewBox, width: box[2], height: box[3], paths}
}

const mark = await artwork('mark')
const wordmark = await artwork('wordmark')
const {colors} = brand
if (typeof brand.name !== 'string' || !brand.name.trim()) {
  throw new Error('Brand name must be a nonempty string')
}
for (const name of [
  'primary',
  'onPrimary',
  'lavender',
  'ink',
  'light',
  'dark',
]) {
  if (!/^#[\da-f]{6}$/i.test(colors[name]))
    throw new Error(`Invalid brand color: ${name}`)
}

function paths(art, fill) {
  return `<g fill="${fill}">${art.paths.map(d => `<path d="${d}"/>`).join('')}</g>`
}

function svg(viewBox, content) {
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${viewBox}">${content}</svg>\n`
}

function markInSquare(size, scale, fill) {
  const width = size * scale
  const height = (width * mark.height) / mark.width
  return `<g transform="translate(${(size - width) / 2} ${(size - height) / 2}) scale(${width / mark.width})">${paths(mark, fill)}</g>`
}

async function emit(name, value) {
  const content = Buffer.isBuffer(value) ? value : Buffer.from(value)
  const target = path.join(output, name)
  if (check) {
    const existing = await readFile(target).catch(() => null)
    if (!existing?.equals(content)) stale.push(name)
  } else {
    await writeFile(target, content)
  }
}

async function png(name, content, width, background) {
  let image = sharp(Buffer.from(content)).resize({width})
  if (background) image = image.flatten({background})
  await emit(name, await image.png().toBuffer())
}

/** Mix opaque RGB colors; all generated token values remain plain hex colors. */
function mix(a, b, amount) {
  const rgb = [1, 3, 5].map(i =>
    Math.round(
      parseInt(a.slice(i, i + 2), 16) * (1 - amount) +
        parseInt(b.slice(i, i + 2), 16) * amount,
    )
      .toString(16)
      .padStart(2, '0'),
  )
  return `#${rgb.join('')}`.toUpperCase()
}

function palette(canvas, ink) {
  const result = {}
  for (const [level, amount] of Object.entries({
    0: 0,
    25: 0.02,
    50: 0.045,
    100: 0.12,
    200: 0.24,
    300: 0.34,
    400: 0.45,
    500: 0.57,
    600: 0.67,
    700: 0.75,
    800: 0.82,
    900: 0.9,
    950: 0.95,
    975: 0.975,
    1000: 1,
  })) {
    result[`contrast_${level}`] = mix(canvas, ink, amount)
  }
  for (const [level, amount] of Object.entries({
    25: 0.97,
    50: 0.92,
    100: 0.84,
    200: 0.68,
    300: 0.46,
    400: 0.24,
  })) {
    result[`primary_${level}`] = mix(colors.primary, '#FFFFFF', amount)
  }
  result.primary_500 = colors.primary
  for (const [level, amount] of Object.entries({
    600: 0.14,
    700: 0.28,
    800: 0.42,
    900: 0.58,
    950: 0.72,
    975: 0.82,
  })) {
    result[`primary_${level}`] = mix(colors.primary, colors.ink, amount)
  }
  return result
}

const lockupWidth = Math.max(mark.width, wordmark.width)
const wordmarkY = mark.height + brand.wordmarkGap
if (!Number.isFinite(brand.wordmarkGap) || brand.wordmarkGap < 0) {
  throw new Error('wordmarkGap must be a nonnegative number')
}
const lockup = {
  viewBox: `0 0 ${lockupWidth} ${wordmarkY + wordmark.height}`,
  width: lockupWidth,
  height: wordmarkY + wordmark.height,
  markX: (lockupWidth - mark.width) / 2,
  wordmarkX: (lockupWidth - wordmark.width) / 2,
  wordmarkY,
}
if (!check) await mkdir(output, {recursive: true})
await emit(
  'artwork.json',
  JSON.stringify({mark, wordmark, lockup}, null, 2) + '\n',
)
await emit(
  'palette.json',
  JSON.stringify(
    {
      defaultPalette: palette(colors.light, colors.ink),
      subduedPalette: palette(colors.light, colors.dark),
    },
    null,
    2,
  ) + '\n',
)

for (const [name, fill] of Object.entries({
  mark: colors.primary,
  'mark-dark': colors.lavender,
  'mark-white': colors.onPrimary,
})) {
  await emit(`${name}.svg`, svg(mark.viewBox, paths(mark, fill)))
}
await emit('wordmark.svg', svg(wordmark.viewBox, paths(wordmark, colors.ink)))

for (const [name, markColor, textColor] of [
  ['logo', colors.primary, colors.ink],
  ['logo-dark', colors.lavender, colors.light],
]) {
  await emit(
    `${name}.svg`,
    svg(
      `0 0 ${lockupWidth} ${wordmarkY + wordmark.height}`,
      `<g transform="translate(${(lockupWidth - mark.width) / 2} 0)">${paths(mark, markColor)}</g>` +
        `<g transform="translate(${(lockupWidth - wordmark.width) / 2} ${wordmarkY})">${paths(wordmark, textColor)}</g>`,
    ),
  )
}

for (const [name, background, foreground] of [
  ['purple', colors.primary, colors.onPrimary],
  ['dark', colors.dark, colors.lavender],
  ['light', colors.light, colors.ink],
  ['black', colors.ink, colors.light],
]) {
  const content = svg(
    '0 0 1024 1024',
    `<path fill="${background}" d="M0 0h1024v1024H0z"/>${markInSquare(1024, 0.68, foreground)}`,
  )
  await emit(`icon-${name}.svg`, content)
  await png(`icon-${name}.png`, content, 1024, background)
  for (const size of [2048, 4096]) {
    const scaled = content.replace(
      '<svg ',
      `<svg width="${size}" height="${size}" `,
    )
    await png(`icon-${name}-${size}.png`, scaled, size, background)
  }
}
await png(
  'adaptive-foreground.png',
  svg('0 0 1024 1024', markInSquare(1024, 0.58, colors.onPrimary)),
  1024,
)
await png(
  'adaptive-monochrome.png',
  svg('0 0 1024 1024', markInSquare(1024, 0.58, '#FFFFFF')),
  1024,
)
await png(
  'notification.png',
  svg('0 0 96 96', markInSquare(96, 0.86, '#FFFFFF')),
  96,
)
await png('splash.png', svg(mark.viewBox, paths(mark, colors.onPrimary)), 306)
await png(
  'splash-dark.png',
  svg(mark.viewBox, paths(mark, colors.lavender)),
  306,
)
await png(
  'favicon.png',
  svg(
    '0 0 192 192',
    `<path fill="${colors.primary}" d="M0 0h192v192H0z"/>${markInSquare(192, 0.68, colors.onPrimary)}`,
  ),
  192,
  colors.primary,
)

const assetPath = name => `./assets/branding/caelum/generated/${name}`
const iconVariants = {
  default_light: 'purple',
  default_dark: 'dark',
  core_aurora: 'purple',
  core_bonfire: 'purple',
  core_sunrise: 'purple',
  core_sunset: 'purple',
  core_midnight: 'dark',
  core_flat_blue: 'purple',
  core_flat_white: 'light',
  core_flat_black: 'black',
  core_classic: 'purple',
}
await emit(
  'build.json',
  JSON.stringify(
    {
      icon: assetPath('icon-purple.png'),
      foreground: assetPath('adaptive-foreground.png'),
      monochrome: assetPath('adaptive-monochrome.png'),
      notification: assetPath('notification.png'),
      splash: assetPath('splash.png'),
      darkSplash: assetPath('splash-dark.png'),
      favicon: assetPath('favicon.png'),
      dynamicIcons: Object.fromEntries(
        Object.entries(iconVariants).map(([id, variant]) => [
          id,
          {
            ios: assetPath(`icon-${variant}.png`),
            android: assetPath(`icon-${variant}.png`),
            prerendered: true,
          },
        ]),
      ),
    },
    null,
    2,
  ) + '\n',
)

if (stale.length) {
  throw new Error(
    `Generated branding is stale: ${stale.join(', ')}. Run pnpm branding:generate.`,
  )
}
console.log(
  check
    ? 'Brand assets are up to date.'
    : 'Generated Caelum SVG, native artwork, palettes, icons and splash assets.',
)
