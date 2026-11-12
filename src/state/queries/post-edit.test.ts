import {describe, expect, it} from '@jest/globals'

import {
  buildEditedPostRecord,
  isPostEditable,
  POST_EDIT_WINDOW_MS,
} from './post-edit-utils'

describe('isPostEditable', () => {
  const now = Date.parse('2026-11-12T12:10:00.000Z')

  it('allows editing a post created within the last 10 minutes', () => {
    expect(
      isPostEditable(
        {
          text: 'hello world',
          createdAt: '2026-11-12T12:05:00.000Z',
        },
        now,
      ),
    ).toBe(true)
  })

  it('allows editing at the exact 10 minute boundary', () => {
    expect(
      isPostEditable(
        {
          text: 'hello world',
          createdAt: new Date(now - POST_EDIT_WINDOW_MS).toISOString(),
        },
        now,
      ),
    ).toBe(true)
  })

  it('disallows editing a post older than 10 minutes', () => {
    expect(
      isPostEditable(
        {
          text: 'hello world',
          createdAt: new Date(now - POST_EDIT_WINDOW_MS - 1).toISOString(),
        },
        now,
      ),
    ).toBe(false)
  })

  it('disallows editing a post with a future createdAt', () => {
    expect(
      isPostEditable(
        {
          text: 'hello world',
          createdAt: new Date(now + 1000).toISOString(),
        },
        now,
      ),
    ).toBe(false)
  })

  it('disallows editing a post that already has originalText or updatedAt', () => {
    expect(
      isPostEditable(
        {
          text: 'hello world',
          createdAt: '2026-11-12T12:05:00.000Z',
          originalText: 'hello',
        },
        now,
      ),
    ).toBe(false)

    expect(
      isPostEditable(
        {
          text: 'hello world',
          createdAt: '2026-11-12T12:05:00.000Z',
          updatedAt: '2026-11-12T12:06:00.000Z',
        },
        now,
      ),
    ).toBe(false)
  })
})

describe('buildEditedPostRecord', () => {
  it('preserves createdAt and metadata while setting originalText and updatedAt', () => {
    const editedAt = new Date('2026-11-12T12:06:00.000Z')
    const result = buildEditedPostRecord({
      currentRecord: {
        $type: 'app.bsky.feed.post',
        text: 'original text',
        createdAt: '2026-11-12T12:00:00.000Z',
        langs: ['en'],
        entities: [{type: 'mention'}],
        embed: {$type: 'app.bsky.embed.images', images: []},
      },
      resolvedText: 'updated text',
      resolvedFacets: undefined,
      now: editedAt,
    })

    expect(result).toEqual({
      $type: 'app.bsky.feed.post',
      text: 'updated text',
      originalText: 'original text',
      createdAt: '2026-11-12T12:00:00.000Z',
      updatedAt: '2026-11-12T12:06:00.000Z',
      langs: ['en'],
      embed: {$type: 'app.bsky.embed.images', images: []},
    })
    expect('entities' in result).toBe(false)
    expect('facets' in result).toBe(false)
  })
})
