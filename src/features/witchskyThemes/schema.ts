import {processColor} from 'react-native'
import {z} from 'zod'

export const THEME_COLLECTION = 'app.witchsky.theme.colors'

/** Normalize native-supported color strings before they reach styles or ramps. */
function normalizeColor(value: string, ctx: z.RefinementCtx): string {
  const color = processColor(value)
  if (typeof color !== 'number') {
    ctx.addIssue({code: z.ZodIssueCode.custom, message: 'Unsupported color'})
    return z.NEVER
  }
  const argb = color >>> 0
  const rgb = (argb & 0xffffff).toString(16).padStart(6, '0')
  const alpha = argb >>> 24
  return `#${rgb}${alpha === 255 ? '' : alpha.toString(16).padStart(2, '0')}`
}

const color = z.string().max(128).transform(normalizeColor)
export const semanticColorsSchema = z.object({
  canvas: color,
  surface: color,
  surfaceRaised: color,
  text: color,
  textMuted: color,
  border: color,
  accent: color,
  accentSoft: color,
  onAccent: color,
  positive: color,
  warning: color,
  critical: color,
  favorite: color,
})
export type SemanticColors = z.infer<typeof semanticColorsSchema>

const variantSchema = z.object({
  name: z.string().trim().min(1).max(48),
  colors: semanticColorsSchema.partial().optional(),
})
const recordSchema = z.object({
  $type: z.literal(THEME_COLLECTION),
  name: z.string().trim().min(1).max(64),
  mode: z.enum(['light', 'dark']),
  base: z.object({
    name: z.string().trim().min(1).max(48),
    colors: semanticColorsSchema,
  }),
  variants: z.array(z.unknown()).max(11).optional(),
  createdAt: z.string().datetime({offset: true}),
})

export const themeOptionSchema = z.object({
  id: z.string().max(2048),
  uri: z.string().startsWith('at://').max(2048),
  name: z.string().max(64),
  variantName: z.string().max(48),
  mode: z.enum(['light', 'dark']),
  colors: semanticColorsSchema,
})
export type ThemeOption = z.infer<typeof themeOptionSchema>
const selectionEnvelopeSchema = z.object({data: themeOptionSchema})

/** Variants can invert the canvas without changing the record's mode field. */
function resolveMode(canvas: string, fallback: 'light' | 'dark') {
  const alpha = canvas.length === 9 ? parseInt(canvas.slice(7, 9), 16) / 255 : 1
  const ground = fallback === 'light' ? 255 : 0
  const linear = [1, 3, 5].map(offset => {
    const channel =
      (parseInt(canvas.slice(offset, offset + 2), 16) * alpha +
        ground * (1 - alpha)) /
      255
    return channel <= 0.04045
      ? channel / 12.92
      : ((channel + 0.055) / 1.055) ** 2.4
  })
  const luminance = linear[0] * 0.2126 + linear[1] * 0.7152 + linear[2] * 0.0722
  return luminance > 0.179 ? 'light' : 'dark'
}

/** Invalid records are isolated; an invalid variant cannot hide a valid base. */
export function parseThemeRecord(uri: string, value: unknown): ThemeOption[] {
  const parsed = recordSchema.safeParse(value)
  if (!parsed.success) return []
  const record = parsed.data
  const base: ThemeOption = {
    id: uri,
    uri,
    name: record.name,
    variantName: record.base.name,
    mode: resolveMode(record.base.colors.canvas, record.mode),
    colors: record.base.colors,
  }
  const options = [base]
  record.variants?.forEach((value, index) => {
    const variant = variantSchema.safeParse(value)
    if (variant.success) {
      const colors = {...base.colors, ...variant.data.colors}
      options.push({
        ...base,
        id: `${uri}#variant-${index}`,
        variantName: variant.data.name,
        colors,
        mode: resolveMode(colors.canvas, record.mode),
      })
    }
  })
  return options
}

/** A cached selection must belong to the active account's theme collection. */
export function readThemeSelection(raw: string | undefined, did: string) {
  if (!raw) return null
  try {
    const parsed = selectionEnvelopeSchema.safeParse(JSON.parse(raw) as unknown)
    if (
      parsed.success &&
      parsed.data.data.uri.startsWith(`at://${did}/${THEME_COLLECTION}/`)
    ) {
      return parsed.data.data
    }
  } catch {
    // Corrupt optional preferences must never prevent startup.
  }
  return null
}
