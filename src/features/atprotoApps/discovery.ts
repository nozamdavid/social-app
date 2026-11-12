import {getBlobCidString} from '@atproto/lex'

import {isInvalidHandle} from '#/lib/strings/handles'
import {type ImageProps} from '#/components/Image'
import {
  APP_COLLECTIONS,
  PUBLICATION_COLLECTION,
  STANDARD_SITE_FAVICON,
} from './apps'

export type DiscoveredApp = {
  id: string
  name: string
  url: string
  /** Image source shown at the start of the entry, if any. */
  icon?: ImageProps['source']
}

/**
 * The handle, or the DID when the handle is missing or invalid, since app
 * profile URLs accept either.
 */
export function getAppIdentifier(did: string, handle?: string): string {
  return handle && !isInvalidHandle(handle) ? handle : did
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function parseHttpUrl(value: unknown): URL | undefined {
  if (typeof value !== 'string') return undefined
  try {
    const url = new URL(value)
    return url.protocol === 'http:' || url.protocol === 'https:'
      ? url
      : undefined
  } catch {
    return undefined
  }
}

/**
 * Best-effort CID of a blob field. Records are untrusted, so this accepts both
 * parsed blob refs and the raw JSON form and returns `undefined` otherwise.
 */
function getBlobCid(icon: unknown): string | undefined {
  if (!isRecord(icon)) return undefined
  const ref = icon.ref
  if (isRecord(ref) && typeof ref.$link === 'string') return ref.$link
  try {
    const cid = getBlobCidString(icon as Parameters<typeof getBlobCidString>[0])
    return typeof cid === 'string' && cid ? cid : undefined
  } catch {
    return undefined
  }
}

/**
 * Direct PDS blob URL. Preferred over the Bluesky CDN, which only serves blobs
 * it has indexed and so fails for arbitrary third-party lexicons.
 */
export function getPdsBlobUrl(pdsUrl: string, did: string, cid: string) {
  const base = pdsUrl.replace(/\/+$/, '')
  return `${base}/xrpc/com.atproto.sync.getBlob?did=${encodeURIComponent(did)}&cid=${encodeURIComponent(cid)}`
}

/**
 * Turn a `site.standard.publication` record into an entry, or `undefined` if
 * it has no usable http(s) `url`.
 */
export function parsePublication({
  rkey,
  value,
  did,
  pdsUrl,
}: {
  rkey: string
  value: unknown
  did: string
  pdsUrl: string
}): DiscoveredApp | undefined {
  if (!isRecord(value)) return undefined
  const url = parseHttpUrl(value.url)
  if (!url) return undefined
  const name =
    typeof value.name === 'string' && value.name.trim()
      ? value.name.trim()
      : url.hostname
  const cid = getBlobCid(value.icon)
  return {
    id: `standard-${rkey}`,
    name,
    url: (value.url as string).trim(),
    icon: cid ? {uri: getPdsBlobUrl(pdsUrl, did, cid)} : STANDARD_SITE_FAVICON,
  }
}

/**
 * Entries for each configured app whose collection is in the repo.
 */
export function getAppsFromCollections({
  collections,
  did,
  handle,
}: {
  collections: string[]
  did: string
  handle?: string
}): DiscoveredApp[] {
  const identifier = getAppIdentifier(did, handle)
  const present = new Set(collections)
  return APP_COLLECTIONS.filter(app => present.has(app.collection)).map(
    app => ({
      id: app.id,
      name: app.name,
      url: app.getUrl(identifier),
      icon: app.icon,
    }),
  )
}

/**
 * Whether the repo has standard.site publications worth listing.
 */
export function hasPublications(collections: string[]) {
  return collections.includes(PUBLICATION_COLLECTION)
}
