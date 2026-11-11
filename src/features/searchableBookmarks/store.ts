import {gunzipSync, gzipSync, strFromU8, strToU8} from 'fflate'

import {app} from '#/lexicons'
import * as bsky from '#/types/bsky'
import {deleteSnapshot, readSnapshot, writeSnapshot} from './database'

export type Bookmark = app.bsky.bookmark.defs.BookmarkView
export type Snapshot = {
  bookmarks: Bookmark[]
  complete: boolean
  cursor?: string
}
export type Status = {
  count: number
  bytes: number
  syncing: boolean
  complete: boolean
  error?: unknown
  revision: number
}
const emptyStatus: Status = {
  count: 0,
  bytes: 0,
  syncing: false,
  complete: false,
  revision: 0,
}
const statuses = new Map<string, Status>()
const listeners = new Set<() => void>()
const running = new Map<string, Promise<void>>()
const generations = new Map<string, number>()
const runningGenerations = new Map<string, number>()
const runningFullSyncs = new Map<string, boolean>()
const updates = new Map<string, Promise<void>>()

export function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}
export function getStatus(key: string) {
  return statuses.get(key) ?? emptyStatus
}
function report(key: string, patch: Partial<Status>) {
  statuses.set(key, {...getStatus(key), ...patch})
  listeners.forEach(listener => listener())
}

export async function load(key: string): Promise<Snapshot> {
  const bytes = await readSnapshot(key)
  const snapshot: Snapshot = bytes
    ? JSON.parse(strFromU8(gunzipSync(bytes)))
    : {bookmarks: [], complete: false}
  if (!getStatus(key).syncing || !runningFullSyncs.get(key)) {
    report(key, {
      count: snapshot.bookmarks.length,
      bytes: bytes?.byteLength ?? 0,
      complete: snapshot.complete,
    })
  }
  return snapshot
}
async function save(key: string, snapshot: Snapshot) {
  const bytes = gzipSync(strToU8(JSON.stringify(snapshot)), {level: 9})
  await writeSnapshot(key, bytes)
  report(key, {
    count: snapshot.bookmarks.length,
    bytes: bytes.byteLength,
    complete: snapshot.complete,
    revision: getStatus(key).revision + 1,
  })
}

/**
 * Initial imports resume; incremental syncs stop at overlap. Full syncs replace
 * the snapshot only after every page succeeds, so remote removals are reconciled.
 */
export function sync(
  key: string,
  fetchPage: (
    cursor?: string,
  ) => Promise<app.bsky.bookmark.getBookmarks.$OutputBody>,
  isEnabled: () => boolean,
  {full = false}: {full?: boolean} = {},
): Promise<void> {
  const existing = running.get(key)
  const generation = generations.get(key) ?? 0
  if (existing) {
    if (runningGenerations.get(key) !== generation) {
      return existing.then(() => sync(key, fetchPage, isEnabled, {full}))
    }
    if (full && !runningFullSyncs.get(key)) {
      return existing.then(() => {
        if (isEnabled() && generation === (generations.get(key) ?? 0)) {
          return sync(key, fetchPage, isEnabled, {full})
        }
      })
    }
    return existing
  }
  const pendingUpdates = updates.get(key)
  const current = () =>
    isEnabled() && generation === (generations.get(key) ?? 0)
  const work = (async () => {
    const previousStatus = getStatus(key)
    report(key, {syncing: true, error: undefined, ...(full && {count: 0})})
    try {
      await pendingUpdates?.catch(() => {})
      let snapshot: Snapshot = full
        ? {bookmarks: [], complete: false}
        : await load(key)
      const known = new Map(
        snapshot.bookmarks.map(b => [b.subject.uri, b.createdAt]),
      )
      const original = snapshot.bookmarks
      const rows = new Map(snapshot.bookmarks.map(b => [b.subject.uri, b]))
      const downloaded = new Map<string, Bookmark>()
      const incremental = !full && snapshot.complete
      let cursor = snapshot.complete ? undefined : snapshot.cursor
      const cursors = new Set<string>()
      do {
        if (!current()) return
        const page = await fetchPage(cursor)
        if (!current()) return
        let overlap = false
        for (const bookmark of page.bookmarks) {
          if (
            incremental &&
            known.has(bookmark.subject.uri) &&
            known.get(bookmark.subject.uri) === bookmark.createdAt
          ) {
            overlap = true
            break
          }
          rows.set(bookmark.subject.uri, bookmark)
          downloaded.set(bookmark.subject.uri, bookmark)
        }
        cursor = overlap ? undefined : page.cursor
        if (cursor && cursors.has(cursor))
          throw new Error('Bookmark pagination stalled')
        if (cursor) cursors.add(cursor)
        snapshot = {
          bookmarks: incremental
            ? [
                ...downloaded.values(),
                ...original.filter(b => !downloaded.has(b.subject.uri)),
              ]
            : [...rows.values()],
          complete: snapshot.complete || !cursor,
          cursor,
        }
        if ((!full && !incremental) || !cursor) {
          await save(key, snapshot)
        } else {
          report(key, {count: rows.size})
        }
      } while (cursor)
    } catch (error) {
      report(key, {
        ...(full && {
          count: previousStatus.count,
          complete: previousStatus.complete,
        }),
        error,
      })
    } finally {
      report(key, {syncing: false})
    }
  })().finally(() => {
    running.delete(key)
  })
  runningFullSyncs.set(key, full)
  runningGenerations.set(key, generation)
  running.set(key, work)
  return work
}

/** Serialize local actions after an import so a late page cannot undo them. */
export function update(key: string, uri: string, bookmark?: Bookmark) {
  const pendingSync = running.get(key)
  const previous = updates.get(key)
  const generation = generations.get(key) ?? 0
  const work = (async () => {
    await pendingSync
    await previous?.catch(() => {})
    if (generation !== (generations.get(key) ?? 0)) return
    const snapshot = await load(key)
    snapshot.bookmarks = snapshot.bookmarks.filter(b => b.subject.uri !== uri)
    if (bookmark) snapshot.bookmarks.unshift(bookmark)
    await save(key, snapshot)
  })()
  updates.set(key, work)
  return work
}

export async function clear(key: string) {
  generations.set(key, (generations.get(key) ?? 0) + 1)
  await running.get(key)
  await updates.get(key)?.catch(() => {})
  await deleteSnapshot(key)
  report(key, {...emptyStatus, revision: getStatus(key).revision + 1})
}

export function normalize(value: string) {
  return value
    .normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
}

/** Match all words across post text, author, image descriptions and links. */
export function matches(bookmark: Bookmark, query: string) {
  if (!query.trim()) return true
  if (!bsky.isType(app.bsky.feed.defs.postView, bookmark.item)) return false
  const post = bookmark.item
  const record = post.record as {text?: string}
  const searchable: string[] = [
    record.text ?? '',
    post.author.handle,
    post.author.displayName ?? '',
    post.author.did,
  ]
  function collect(value: unknown) {
    if (!value || typeof value !== 'object') return
    for (const [field, content] of Object.entries(value)) {
      if (
        ['alt', 'title', 'description', 'uri'].includes(field) &&
        typeof content === 'string'
      ) {
        searchable.push(content)
      } else if (content && typeof content === 'object') {
        collect(content)
      }
    }
  }
  collect(post.embed)
  collect(post.record)
  const text = normalize(searchable.join(' '))
  return normalize(query)
    .trim()
    .split(/\s+/)
    .every(term => text.includes(term))
}

export function stop(key: string) {
  generations.set(key, (generations.get(key) ?? 0) + 1)
}
