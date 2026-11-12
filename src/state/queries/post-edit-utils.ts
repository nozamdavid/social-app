import {type LexMap} from '@atproto/lex'
import {toDatetimeString} from '@atproto/syntax'

import {type app} from '#/lexicons'

export const POST_EDIT_WINDOW_MS = 10 * 60 * 1000

export function isPostEditable(
  record:
    app.bsky.feed.post.Main | LexMap | Record<string, unknown> | undefined,
  now = Date.now(),
): boolean {
  if (!record || typeof record !== 'object') {
    return false
  }
  const obj = record as Record<string, unknown>
  if (typeof obj.text !== 'string' || typeof obj.createdAt !== 'string') {
    return false
  }
  if (Object.hasOwn(obj, 'originalText') || Object.hasOwn(obj, 'updatedAt')) {
    return false
  }
  const created = Date.parse(obj.createdAt)
  const age = now - created
  return Number.isFinite(created) && age >= 0 && age <= POST_EDIT_WINDOW_MS
}

export function buildEditedPostRecord({
  currentRecord,
  resolvedText,
  resolvedFacets,
  now = new Date(),
}: {
  currentRecord: LexMap & {text: string}
  resolvedText: string
  resolvedFacets?: app.bsky.richtext.facet.Main[]
  now?: Date
}): LexMap {
  const updatedRecord: LexMap = {
    ...currentRecord,
    $type: 'app.bsky.feed.post',
    text: resolvedText,
    originalText:
      typeof currentRecord.originalText === 'string'
        ? currentRecord.originalText
        : currentRecord.text,
    updatedAt: toDatetimeString(now),
  }
  delete updatedRecord.entities
  if (resolvedFacets && resolvedFacets.length > 0) {
    updatedRecord.facets = resolvedFacets
  } else {
    delete updatedRecord.facets
  }
  return updatedRecord
}
