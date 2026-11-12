import {createElement} from 'react'
import {type AtUriString} from '@atproto/syntax'
import {deleteLike, deleteRepost, like, repost} from '@bsky/sdk'
import {
  notifyManager,
  QueryClient,
  QueryClientProvider,
} from '@tanstack/react-query'
import {act, renderHook} from '@testing-library/react-native'

import {castAsShadow} from '#/state/cache/types'
import {
  usePostLikeMutationQueue,
  usePostRepostMutationQueue,
} from '#/state/queries/post'
import * as userActionHistory from '#/state/userActionHistory'
import * as Toast from '#/components/Toast'
import {type app} from '#/lexicons'

const likeUri = 'at://did:plc:me/app.bsky.feed.like/1' as AtUriString
const repostUri = 'at://did:plc:me/app.bsky.feed.repost/1' as AtUriString
let mockEnabled = false
let mockLikeUri: string | undefined
function mockDefaultCall(action: unknown, _input: unknown) {
  return Promise.resolve(
    action === repost
      ? {uri: repostUri}
      : action === like
        ? {uri: likeUri}
        : undefined,
  )
}
const mockCall = jest.fn(mockDefaultCall)

jest.mock('@bsky/sdk', () => ({
  like: 'like',
  repost: 'repost',
  deleteLike: 'deleteLike',
  deleteRepost: 'deleteRepost',
  deletePost: 'deletePost',
}))
jest.mock('@lingui/react', () => ({
  useLingui: () => ({
    _: (message: {message: string}) => message.message,
    i18n: {_: (message: {message: string}) => message.message},
  }),
}))
jest.mock('#/state/session', () => ({
  usePdsClient: () => ({call: mockCall}),
  useSession: () => ({currentAccount: {did: 'did:plc:me'}}),
}))
jest.mock('#/features/customization/preferences', () => ({
  getAutoLikeReposts: () => mockEnabled,
}))
jest.mock('#/state/cache/post-shadow', () => ({
  getPostLikeUri: () => mockLikeUri,
  updatePostShadow: (
    _client: unknown,
    _uri: string,
    shadow: {likeUri?: string},
  ) => {
    if ('likeUri' in shadow) mockLikeUri = shadow.likeUri
  },
}))
jest.mock('#/state/queries/profile', () => ({
  findProfileQueryData: () => undefined,
}))
jest.mock('#/state/cache/thread-mutes', () => ({}))
jest.mock('#/state/userActionHistory', () => ({
  like: jest.fn(),
  unlike: jest.fn(),
}))
jest.mock('#/analytics', () => ({useAnalytics: () => ({metric: jest.fn()})}))
jest.mock('#/analytics/metrics', () => ({toClout: () => undefined}))
jest.mock('#/components/Toast', () => ({show: jest.fn()}))

beforeAll(() => notifyManager.setScheduler(callback => callback()))
afterAll(() =>
  notifyManager.setScheduler(callback => {
    setTimeout(callback, 0)
  }),
)

beforeEach(() => {
  mockEnabled = false
  mockLikeUri = undefined
  jest.clearAllMocks()
  mockCall.mockImplementation(mockDefaultCall)
})

function setup() {
  const client = new QueryClient({defaultOptions: {mutations: {retry: false}}})
  const post = castAsShadow({
    uri: 'at://did:plc:author/app.bsky.feed.post/1',
    cid: 'bafypost',
    author: {did: 'did:plc:author', handle: 'author.test'},
    viewer: {like: mockLikeUri},
    record: {},
    indexedAt: '2026-11-10T12:00:00Z',
  } as app.bsky.feed.defs.PostView)
  return renderHook(
    () => {
      const [manualLike, manualUnlike] = usePostLikeMutationQueue(
        post,
        undefined,
        'feed',
        'FeedItem',
      )
      const [queueRepost, queueUnrepost] = usePostRepostMutationQueue(
        post,
        undefined,
        'feed',
        'FeedItem',
      )
      return {manualLike, manualUnlike, queueRepost, queueUnrepost}
    },
    {
      wrapper: ({children}) =>
        createElement(QueryClientProvider, {client}, children),
    },
  )
}

it('keeps stock repost behavior when the preference is disabled', async () => {
  const {result} = setup()
  await act(async () =>
    expect(await result.current.queueRepost()).toBe(repostUri),
  )
  expect(mockCall.mock.calls.map(([action]) => action)).toEqual([repost])
})

it('likes after a successful repost and records the ordinary like action', async () => {
  mockEnabled = true
  const {result} = setup()
  await act(async () => {
    await result.current.queueRepost()
  })
  expect(mockCall.mock.calls.map(([action]) => action)).toEqual([repost, like])
  expect(mockLikeUri).toBe(likeUri)
  expect(userActionHistory.like).toHaveBeenCalledWith([
    'at://did:plc:author/app.bsky.feed.post/1',
  ])
})

it.each([likeUri, 'pending'])(
  'does not like an already liked or pending post (%s)',
  async existing => {
    mockEnabled = true
    mockLikeUri = existing
    const {result} = setup()
    await act(async () => {
      await result.current.queueRepost()
    })
    expect(mockCall.mock.calls.map(([action]) => action)).toEqual([repost])
  },
)

it('does not like when reposting fails', async () => {
  mockEnabled = true
  mockCall.mockRejectedValueOnce(new Error('Repost failed'))
  const {result} = setup()
  await act(async () => {
    await expect(result.current.queueRepost()).rejects.toThrow('Repost failed')
  })
  expect(mockCall).toHaveBeenCalledTimes(1)
})

it('keeps a successful repost when liking fails and explains the partial success', async () => {
  mockEnabled = true
  mockCall.mockImplementation(action =>
    action === repost
      ? Promise.resolve({uri: repostUri})
      : Promise.reject(new Error('Like failed')),
  )
  const {result} = setup()
  await act(async () =>
    expect(await result.current.queueRepost()).toBe(repostUri),
  )
  expect(mockLikeUri).toBeUndefined()
  expect(Toast.show).toHaveBeenCalledWith(
    expect.stringContaining('couldn’t like'),
    {type: 'warning'},
  )
})

it('shares the manual like queue so an unlike while auto-liking deletes the real record', async () => {
  mockEnabled = true
  let finishLike!: (value: {uri: AtUriString}) => void
  let startedLike!: () => void
  const started = new Promise<void>(resolve => {
    startedLike = resolve
  })
  const pending = new Promise<{uri: AtUriString}>(resolve => {
    finishLike = resolve
  })
  mockCall.mockImplementation(async action => {
    if (action === repost) return {uri: repostUri}
    if (action === like) {
      startedLike()
      return pending
    }
    return undefined
  })
  const {result} = setup()
  await act(async () => {
    const reposting = result.current.queueRepost()
    await started
    const unliking = result.current.manualUnlike()
    finishLike({uri: likeUri})
    await Promise.all([reposting, unliking])
  })
  expect(mockCall.mock.calls.map(([action]) => action)).toEqual([
    repost,
    like,
    deleteLike,
  ])
  expect(mockCall).toHaveBeenLastCalledWith(deleteLike, likeUri)
  expect(mockLikeUri).toBeUndefined()
})

it('avoids a duplicate auto-like when a manual like finishes during reposting', async () => {
  mockEnabled = true
  let finishRepost!: (value: {uri: AtUriString}) => void
  const pending = new Promise<{uri: AtUriString}>(resolve => {
    finishRepost = resolve
  })
  mockCall.mockImplementation(async action => {
    if (action === repost) return pending
    if (action === like) return {uri: likeUri}
    return undefined
  })
  const {result} = setup()
  await act(async () => {
    const reposting = result.current.queueRepost()
    await result.current.manualLike()
    finishRepost({uri: repostUri})
    await reposting
  })
  expect(mockCall.mock.calls.map(([action]) => action)).toEqual([repost, like])
})

it('undoes a repost without removing a like', async () => {
  mockEnabled = true
  const {result} = setup()
  await act(async () => {
    const reposting = result.current.queueRepost()
    const removing = result.current.queueUnrepost()
    await Promise.all([reposting, removing])
  })
  expect(mockCall.mock.calls.map(([action]) => action)).toEqual([
    repost,
    like,
    deleteRepost,
  ])
  expect(mockLikeUri).toBe(likeUri)
})
