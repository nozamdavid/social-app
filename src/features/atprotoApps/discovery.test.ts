import {APP_COLLECTIONS, STANDARD_SITE_FAVICON} from './apps'
import {
  getAppIdentifier,
  getAppsFromCollections,
  getPdsBlobUrl,
  parsePublication,
} from './discovery'

const did = 'did:plc:abc'
const pdsUrl = 'https://pds.example.com/'

describe('getAppsFromCollections', () => {
  it('maps present collections to apps using the handle', () => {
    const apps = getAppsFromCollections({
      collections: [
        'app.bsky.feed.post',
        'org.tangled.actor.profile',
        'social.grain.actor.profile',
        'id.sifa.profile.self',
      ],
      did,
      handle: 'alice.test',
    })
    expect(apps.map(a => [a.id, a.url])).toEqual([
      ['tangled', 'https://tangled.org/alice.test'],
      ['grain', 'https://grain.social/profile/alice.test'],
      ['sifa', 'https://sifa.id/p/alice.test'],
    ])
    expect(apps.map(a => a.icon)).toEqual(APP_COLLECTIONS.map(a => a.icon))
    for (const app of apps) {
      expect(app.icon).toBeDefined()
    }
  })

  it('returns nothing when no known collection is present', () => {
    expect(
      getAppsFromCollections({collections: ['app.bsky.feed.post'], did}),
    ).toEqual([])
  })

  it('falls back to the DID for invalid or missing handles', () => {
    expect(getAppIdentifier(did, 'handle.invalid')).toBe(did)
    expect(getAppIdentifier(did)).toBe(did)
    expect(getAppIdentifier(did, 'a.test')).toBe('a.test')
    const [app] = getAppsFromCollections({
      collections: ['org.tangled.actor.profile'],
      did,
      handle: 'handle.invalid',
    })
    expect(app.url).toBe(`https://tangled.org/${did}`)
  })
})

describe('parsePublication', () => {
  const parse = (value: unknown) =>
    parsePublication({rkey: 'r1', value, did, pdsUrl})

  it('uses the record name and trimmed url, with the bundled standard.site favicon', () => {
    expect(parse({name: ' My Blog ', url: ' https://blog.test/ '})).toEqual({
      id: 'standard-r1',
      name: 'My Blog',
      url: 'https://blog.test/',
      icon: STANDARD_SITE_FAVICON,
    })
  })

  it('falls back to the hostname when the name is missing or blank', () => {
    expect(parse({name: '  ', url: 'https://blog.test/x'})?.name).toBe(
      'blog.test',
    )
    expect(parse({url: 'https://blog.test/x'})?.name).toBe('blog.test')
    expect(parse({name: 5, url: 'https://blog.test/x'})?.name).toBe('blog.test')
  })

  it('skips records without a valid http(s) url', () => {
    expect(parse({name: 'x'})).toBeUndefined()
    expect(parse({name: 'x', url: 'javascript:alert(1)'})).toBeUndefined()
    expect(parse({name: 'x', url: 'not a url'})).toBeUndefined()
    expect(parse({name: 'x', url: 42})).toBeUndefined()
    expect(parse(null)).toBeUndefined()
    expect(parse('str')).toBeUndefined()
  })

  it('uses the PDS blob URL when the record has an icon', () => {
    const app = parse({
      url: 'https://blog.test',
      icon: {
        $type: 'blob',
        ref: {$link: 'bafkreiexample'},
        mimeType: 'image/png',
        size: 1,
      },
    })
    expect(app?.icon).toEqual({
      uri: 'https://pds.example.com/xrpc/com.atproto.sync.getBlob?did=did%3Aplc%3Aabc&cid=bafkreiexample',
    })
  })

  it('ignores malformed icons', () => {
    expect(parse({url: 'https://blog.test', icon: 'nope'})?.icon).toBe(
      STANDARD_SITE_FAVICON,
    )
    expect(parse({url: 'https://blog.test', icon: {ref: 1}})?.icon).toBe(
      STANDARD_SITE_FAVICON,
    )
  })
})

it('builds blob URLs without a doubled slash', () => {
  expect(getPdsBlobUrl('https://p.test///', 'did:web:x', 'c')).toBe(
    'https://p.test/xrpc/com.atproto.sync.getBlob?did=did%3Aweb%3Ax&cid=c',
  )
})
