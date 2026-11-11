import {type AtUriString} from '@atproto/syntax'
import {QueryClient} from '@tanstack/react-query'

import {getPostLikeUri, updatePostShadow} from '#/state/cache/post-shadow'
import {type app} from '#/lexicons'

let mockPosts: app.bsky.feed.defs.PostView[] = []
jest.mock('#/state/queries/post-feed', () => ({
  findAllPostsInQueryData: function* () {
    yield* mockPosts
  },
}))
jest.mock('#/state/queries/bookmarks/useBookmarksQuery', () => ({
  findAllPostsInQueryData: function* () {},
}))
jest.mock('#/state/queries/explore-feed-previews', () => ({
  findAllPostsInQueryData: function* () {},
}))
jest.mock('#/state/queries/notifications/feed', () => ({
  findAllPostsInQueryData: function* () {},
}))
jest.mock('#/state/queries/notifications/grouped', () => ({
  findAllPostsInQueryData: function* () {},
}))
jest.mock('#/state/queries/post-quotes', () => ({
  findAllPostsInQueryData: function* () {},
}))
jest.mock('#/state/queries/search-posts-v2', () => ({
  findAllPostsInQueryData: function* () {},
}))
jest.mock('#/state/queries/usePostThread/queryCache', () => ({
  findAllPostsInQueryData: function* () {},
}))

function post(like?: AtUriString): app.bsky.feed.defs.PostView {
  return {
    $type: 'app.bsky.feed.defs#postView',
    uri: 'at://did:plc:author/app.bsky.feed.post/1',
    cid: 'bafypost',
    author: {
      $type: 'app.bsky.actor.defs#profileViewBasic',
      did: 'did:plc:author',
      handle: 'author.test',
    },
    record: {},
    indexedAt: '2026-11-10T12:00:00Z',
    viewer: {like},
  }
}

beforeEach(() => {
  mockPosts = []
})

it('reads live optimistic and confirmed likes instead of the captured post snapshot', () => {
  const client = new QueryClient()
  const cached = post()
  const staleSnapshot = post()
  mockPosts = [cached]
  expect(getPostLikeUri(client, staleSnapshot)).toBeUndefined()
  updatePostShadow(client, cached.uri, {likeUri: 'pending'})
  expect(getPostLikeUri(client, staleSnapshot)).toBe('pending')
  const record = 'at://did:plc:me/app.bsky.feed.like/1'
  updatePostShadow(client, cached.uri, {likeUri: record})
  expect(getPostLikeUri(client, staleSnapshot)).toBe(record)
  updatePostShadow(client, cached.uri, {likeUri: undefined})
  expect(getPostLikeUri(client, staleSnapshot)).toBeUndefined()
})

it('uses existing likes from refreshed cache entries and falls back for uncached posts', () => {
  const client = new QueryClient()
  const record = 'at://did:plc:me/app.bsky.feed.like/1'
  mockPosts = [post(record)]
  expect(getPostLikeUri(client, post())).toBe(record)
  mockPosts = []
  expect(getPostLikeUri(client, post(record))).toBe(record)
})
