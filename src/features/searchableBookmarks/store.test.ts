import {toDatetimeString} from '@atproto/syntax'
import {gzipSync, strToU8} from 'fflate'

import {readSnapshot, writeSnapshot} from './database'
import {
  type Bookmark,
  clear,
  getStatus,
  load,
  matches,
  stop,
  sync,
  update,
} from './store'

jest.mock('./database', () => {
  const rows = new Map<string, Uint8Array>()
  return {
    readSnapshot: jest.fn(
      async (key: string) => await Promise.resolve(rows.get(key)),
    ),
    writeSnapshot: jest.fn(async (key: string, value: Uint8Array) => {
      await Promise.resolve(rows.set(key, value))
    }),
    deleteSnapshot: jest.fn(async (key: string) => {
      await Promise.resolve(rows.delete(key))
    }),
  }
})

function bookmark(id: number, text = 'hello'): Bookmark {
  return {
    subject: {uri: `at://did:plc:test/app.bsky.feed.post/${id}`, cid: 'cid'},
    createdAt: toDatetimeString(new Date(id * 1000)),
    item: {
      $type: 'app.bsky.feed.defs#postView',
      uri: `at://did:plc:test/app.bsky.feed.post/${id}`,
      cid: 'cid',
      author: {did: 'did:plc:test', handle: 'alice.test', displayName: 'Álice'},
      record: {
        $type: 'app.bsky.feed.post',
        text,
        createdAt: toDatetimeString(new Date(0)),
      },
      indexedAt: toDatetimeString(new Date(0)),
    },
  }
}

it('compresses, restores and searches bookmarks across unloaded pages', async () => {
  const page = jest
    .fn()
    .mockResolvedValueOnce({bookmarks: [bookmark(3)], cursor: 'next'})
    .mockResolvedValueOnce({
      bookmarks: [bookmark(2, 'A needle in the haystack'), bookmark(1)],
    })
  await sync('initial', page, () => true)
  expect(page).toHaveBeenNthCalledWith(1, undefined)
  expect(page).toHaveBeenNthCalledWith(2, 'next')
  const snapshot = await load('initial')
  expect(snapshot.complete).toBe(true)
  expect(
    snapshot.bookmarks.filter(b => matches(b, 'NEEDLE alice')),
  ).toHaveLength(1)
  expect(matches(snapshot.bookmarks[0], 'missing')).toBe(false)
  const compressed = await readSnapshot('initial')
  expect(compressed!.byteLength).toBeLessThan(JSON.stringify(snapshot).length)
  expect(getStatus('initial').bytes).toBe(compressed!.byteLength)
})

it('stops incremental retrieval at the first known saved record', async () => {
  await sync(
    'overlap',
    () => Promise.resolve({bookmarks: [bookmark(2), bookmark(1)]}),
    () => true,
  )
  const page = jest.fn(() =>
    Promise.resolve({
      bookmarks: [bookmark(3), bookmark(2)],
      cursor: 'unused',
    }),
  )
  await sync('overlap', page, () => true)
  expect(page).toHaveBeenCalledTimes(1)
  expect((await load('overlap')).bookmarks.map(b => b.createdAt)).toEqual(
    [3, 2, 1].map(n => bookmark(n).createdAt),
  )
})

it('resumes an interrupted initial import without treating its partial rows as overlap', async () => {
  const page = jest
    .fn()
    .mockResolvedValueOnce({bookmarks: [bookmark(3)], cursor: 'resume'})
    .mockRejectedValueOnce(new Error('offline'))
  await sync('resume', page, () => true)
  expect((await load('resume')).complete).toBe(false)
  const retry = jest.fn(() =>
    Promise.resolve({bookmarks: [bookmark(2), bookmark(1)]}),
  )
  await sync('resume', retry, () => true)
  expect(retry).toHaveBeenCalledWith('resume')
  expect((await load('resume')).bookmarks).toHaveLength(3)
})

it('does not commit partial incremental syncs that would skip unseen rows on retry', async () => {
  await sync(
    'atomic',
    () => Promise.resolve({bookmarks: [bookmark(1)]}),
    () => true,
  )
  await sync(
    'atomic',
    jest
      .fn()
      .mockResolvedValueOnce({bookmarks: [bookmark(4)], cursor: 'next'})
      .mockRejectedValueOnce(new Error('offline')),
    () => true,
  )
  expect((await load('atomic')).bookmarks).toHaveLength(1)
  expect(getStatus('atomic').error).toBeTruthy()
})

it('isolates accounts/sources, updates actions locally and clears the stored snapshot', async () => {
  await sync(
    'account-a/source-a',
    () => Promise.resolve({bookmarks: [bookmark(1)]}),
    () => true,
  )
  expect((await load('account-b/source-a')).bookmarks).toHaveLength(0)
  expect((await load('account-a/source-b')).bookmarks).toHaveLength(0)
  await update('account-a/source-a', bookmark(2).subject.uri, bookmark(2))
  await update('account-a/source-a', bookmark(1).subject.uri)
  expect((await load('account-a/source-a')).bookmarks).toEqual([bookmark(2)])
  await clear('account-a/source-a')
  expect(await readSnapshot('account-a/source-a')).toBeUndefined()
})

it('discards a late response after disabling or changing account', async () => {
  let started!: () => void
  const ready = new Promise<void>(resolve => {
    started = resolve
  })
  let respond!: (value: {bookmarks: Bookmark[]}) => void
  const pending = sync(
    'cancel',
    () =>
      new Promise(resolve => {
        respond = resolve
        started()
      }),
    () => true,
  )
  await ready
  stop('cancel')
  respond({bookmarks: [bookmark(1)]})
  await pending
  expect((await load('cancel')).bookmarks).toHaveLength(0)
})

it('deduplicates concurrent syncs and rejects pagination loops', async () => {
  const page = jest.fn(() =>
    Promise.resolve({bookmarks: [bookmark(1)], cursor: 'same'}),
  )
  const first = sync('loop', page, () => true)
  expect(sync('loop', page, () => true)).toBe(first)
  await first
  expect(page).toHaveBeenCalledTimes(2)
  expect(getStatus('loop').error).toBeTruthy()
})

it('reports corrupt storage without overwriting it', async () => {
  await writeSnapshot('corrupt', gzipSync(strToU8('{broken')))
  await sync('corrupt', jest.fn(), () => true)
  expect(getStatus('corrupt').error).toBeTruthy()
})

it('preserves collection order when saved timestamps are absent', async () => {
  const first = {...bookmark(1), createdAt: undefined}
  const second = {...bookmark(2), createdAt: undefined}
  await sync(
    'no-dates',
    () => Promise.resolve({bookmarks: [first]}),
    () => true,
  )
  const page = jest.fn(() =>
    Promise.resolve({bookmarks: [second, first], cursor: 'unused'}),
  )
  await sync('no-dates', page, () => true)
  expect(page).toHaveBeenCalledTimes(1)
  expect((await load('no-dates')).bookmarks).toEqual([second, first])
})

it('serializes concurrent local saves without losing either post', async () => {
  await Promise.all([
    update('actions', bookmark(1).subject.uri, bookmark(1)),
    update('actions', bookmark(2).subject.uri, bookmark(2)),
  ])
  expect((await load('actions')).bookmarks).toEqual([bookmark(2), bookmark(1)])
})

it('full sync reads beyond known bookmarks and replaces the collection to remove deletions', async () => {
  const key = 'full-replace'
  await sync(
    key,
    () => Promise.resolve({bookmarks: [bookmark(3), bookmark(2), bookmark(1)]}),
    () => true,
  )
  const refreshed = bookmark(3, 'updated post')
  const page = jest
    .fn()
    .mockResolvedValueOnce({bookmarks: [refreshed], cursor: 'older'})
    .mockResolvedValueOnce({bookmarks: [bookmark(1)]})
  await sync(key, page, () => true, {full: true})
  expect(page).toHaveBeenNthCalledWith(1, undefined)
  expect(page).toHaveBeenNthCalledWith(2, 'older')
  expect((await load(key)).bookmarks).toEqual([refreshed, bookmark(1)])
  expect(getStatus(key).complete).toBe(true)
})

it('keeps the existing collection readable until a full sync finishes', async () => {
  const key = 'full-atomic'
  const old = [bookmark(2), bookmark(1)]
  await sync(
    key,
    () => Promise.resolve({bookmarks: old}),
    () => true,
  )
  const page = jest.fn(async (cursor?: string) => {
    if (!cursor) return {bookmarks: [bookmark(3)], cursor: 'next'}
    expect(getStatus(key).count).toBe(1)
    expect((await load(key)).bookmarks).toEqual(old)
    expect(getStatus(key).count).toBe(1)
    return {bookmarks: [bookmark(1)]}
  })
  await sync(key, page, () => true, {full: true})
  expect((await load(key)).bookmarks).toEqual([bookmark(3), bookmark(1)])
})

it('retains the old snapshot and retries full sync from the beginning after failure', async () => {
  const key = 'full-retry'
  const old = [bookmark(2), bookmark(1)]
  await sync(
    key,
    () => Promise.resolve({bookmarks: old}),
    () => true,
  )
  const revision = getStatus(key).revision
  await sync(
    key,
    jest
      .fn()
      .mockResolvedValueOnce({bookmarks: [bookmark(2)], cursor: 'next'})
      .mockRejectedValueOnce(new Error('offline')),
    () => true,
    {full: true},
  )
  expect(getStatus(key)).toMatchObject({
    count: 2,
    complete: true,
    revision,
    syncing: false,
  })
  expect(getStatus(key).error).toBeTruthy()
  expect((await load(key)).bookmarks).toEqual(old)
  const retry = jest.fn(() => Promise.resolve({bookmarks: [bookmark(1)]}))
  await sync(key, retry, () => true, {full: true})
  expect(retry).toHaveBeenCalledWith(undefined)
  expect((await load(key)).bookmarks).toEqual([bookmark(1)])
})

it('full sync removes all local bookmarks when the remote collection is empty', async () => {
  const key = 'full-empty'
  await sync(
    key,
    () => Promise.resolve({bookmarks: [bookmark(1)]}),
    () => true,
  )
  await sync(
    key,
    () => Promise.resolve({bookmarks: []}),
    () => true,
    {full: true},
  )
  expect(await load(key)).toEqual({bookmarks: [], complete: true})
})

it('queues a requested full sync after an in-flight incremental sync', async () => {
  const key = 'full-queued'
  await sync(
    key,
    () => Promise.resolve({bookmarks: [bookmark(1)]}),
    () => true,
  )
  let respond!: (page: {bookmarks: Bookmark[]}) => void
  let started!: () => void
  const ready = new Promise<void>(resolve => {
    started = resolve
  })
  const incremental = sync(
    key,
    () =>
      new Promise(resolve => {
        respond = resolve
        started()
      }),
    () => true,
  )
  await ready
  const fullPage = jest.fn(() => Promise.resolve({bookmarks: []}))
  const full = sync(key, fullPage, () => true, {full: true})
  expect(fullPage).not.toHaveBeenCalled()
  respond({bookmarks: [bookmark(1)]})
  await Promise.all([incremental, full])
  expect(fullPage).toHaveBeenCalledTimes(1)
  expect((await load(key)).bookmarks).toEqual([])
})

it('full sync can replace a corrupt local snapshot', async () => {
  const key = 'full-repair'
  await writeSnapshot(key, gzipSync(strToU8('{broken')))
  await sync(
    key,
    () => Promise.resolve({bookmarks: [bookmark(1)]}),
    () => true,
    {full: true},
  )
  expect((await load(key)).bookmarks).toEqual([bookmark(1)])
  expect(getStatus(key).error).toBeUndefined()
})
