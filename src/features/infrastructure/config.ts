export type InfrastructureSettings = {
  useLinkRedirects: boolean
  appviewHostname: string
  imageCdnHostname: string
  plcDirectoryHostname: string
}

export const DEFAULT_INFRASTRUCTURE: InfrastructureSettings = {
  useLinkRedirects: true,
  appviewHostname: '',
  imageCdnHostname: '',
  plcDirectoryHostname: '',
}

export const APPVIEW_PRESETS = [
  {name: 'Bluesky', hostname: ''},
  {name: 'Eurosky', hostname: 'api.eurosky.network'},
  {name: 'Blacksky', hostname: 'api.blacksky.community'},
] as const

export const IMAGE_CDN_PRESETS = [
  {name: 'Bluesky', hostname: ''},
  {name: 'Luna', hostname: 'luna.danielmorrisey.com'},
] as const

export const PLC_DIRECTORY_PRESETS = [
  {name: 'plc.directory', hostname: ''},
  {name: 'Eurosky', hostname: 'plc.eurosky.network'},
  {name: 'Wafflehouse.dev', hostname: 'plc.wafflehouse.dev'},
] as const

export function plcDirectoryOrigin(hostname: string) {
  return `https://${hostname || 'plc.directory'}`
}

/** Keep account cache cleanup intact while rejecting a different AppView's data. */
export function appviewCacheBuster(version: string, hostname: string) {
  return hostname ? `${version};appview=${hostname}` : version
}

/** Accept a DNS hostname or HTTPS origin, without paths, credentials or ports. */
export function normalizeHostname(input: string): string {
  if (!input.trim()) return ''
  const authority = input
    .trim()
    .replace(/^https:\/\//i, '')
    .replace(/\/$/, '')
  if (!authority) throw new Error('Invalid hostname')
  if (/[\s:/\\@?#%]/.test(authority)) throw new Error('Invalid hostname')
  const hostname = new URL(`https://${authority}`).hostname
    .toLowerCase()
    .replace(/\.$/, '')
  if (
    hostname.length > 253 ||
    !hostname.includes('.') ||
    !hostname
      .split('.')
      .every(label => /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/.test(label))
  ) {
    throw new Error('Invalid hostname')
  }
  return hostname
}

/** Empty values represent the app's existing Bluesky defaults. */
export function normalizeAppview(input: string): string {
  const hostname = normalizeHostname(input)
  return ['api.bsky.app', 'public.api.bsky.app'].includes(hostname)
    ? ''
    : hostname
}

export function normalizeImageCdn(input: string): string {
  const hostname = normalizeHostname(input)
  return hostname === 'cdn.bsky.app' ? '' : hostname
}

export function normalizePlcDirectory(input: string): string {
  const hostname = normalizeHostname(input)
  return hostname === 'plc.directory' ? '' : hostname
}

/** Ignore malformed stored fields so a damaged preference cannot break startup. */
export function readInfrastructureSettings(
  value: unknown,
): InfrastructureSettings {
  if (!value || typeof value !== 'object') return DEFAULT_INFRASTRUCTURE
  const fields = value as Partial<InfrastructureSettings>
  let appviewHostname = ''
  let imageCdnHostname = ''
  let plcDirectoryHostname = ''
  try {
    if (typeof fields.appviewHostname === 'string')
      appviewHostname = normalizeAppview(fields.appviewHostname)
  } catch {}
  try {
    if (typeof fields.imageCdnHostname === 'string')
      imageCdnHostname = normalizeImageCdn(fields.imageCdnHostname)
  } catch {}
  try {
    if (typeof fields.plcDirectoryHostname === 'string')
      plcDirectoryHostname = normalizePlcDirectory(fields.plcDirectoryHostname)
  } catch {}
  return {
    useLinkRedirects: fields.useLinkRedirects !== false,
    appviewHostname,
    imageCdnHostname,
    plcDirectoryHostname,
  }
}

/** Replace only Bluesky's image CDN, preserving each preset, CID and query. */
export function rewriteImageUrl(uri: string, hostname: string): string {
  if (
    !hostname ||
    !/^https:\/\/cdn\.bsky\.app\/img\/[a-z_]+\/plain\//.test(uri)
  )
    return uri
  return `https://${hostname}${uri.slice('https://cdn.bsky.app'.length)}`
}
