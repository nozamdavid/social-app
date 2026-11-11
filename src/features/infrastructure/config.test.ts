import {
  appviewCacheBuster,
  DEFAULT_INFRASTRUCTURE,
  normalizeAppview,
  normalizeHostname,
  normalizeImageCdn,
  normalizePlcDirectory,
  PLC_DIRECTORY_PRESETS,
  plcDirectoryOrigin,
  readInfrastructureSettings,
  rewriteImageUrl,
} from './config'
import {rewriteImageSource} from './imageSources'

it('keeps Bluesky and redirect defaults, including damaged stored settings', () => {
  expect(readInfrastructureSettings(undefined)).toEqual(DEFAULT_INFRASTRUCTURE)
  expect(
    readInfrastructureSettings({
      appviewHostname: 'http://bad/path',
      imageCdnHostname: 'https://',
      useLinkRedirects: false,
    }),
  ).toEqual({...DEFAULT_INFRASTRUCTURE, useLinkRedirects: false})
  expect(
    readInfrastructureSettings({
      appviewHostname: 'HTTPS://API.EUROSKY.NETWORK/',
      imageCdnHostname: 'cdn.example.com',
    }),
  ).toEqual({
    ...DEFAULT_INFRASTRUCTURE,
    appviewHostname: 'api.eurosky.network',
    imageCdnHostname: 'cdn.example.com',
  })
})

it('normalizes HTTPS hostnames and explicit default providers', () => {
  expect(normalizeHostname(' HTTPS://API.Example.COM./ ')).toBe(
    'api.example.com',
  )
  expect(normalizeAppview('https://public.api.bsky.app/')).toBe('')
  expect(normalizeAppview('api.bsky.app')).toBe('')
  expect(normalizeImageCdn('https://cdn.bsky.app/')).toBe('')
  expect(normalizePlcDirectory('https://PLC.DIRECTORY/')).toBe('')
  expect(normalizePlcDirectory('https://PLC.EUROSKY.NETWORK/')).toBe(
    'plc.eurosky.network',
  )
  expect(normalizeHostname('')).toBe('')
})

it('migrates stored settings without PLC choices and validates the new field independently', () => {
  expect(
    readInfrastructureSettings({imageCdnHostname: 'cdn.example.com'}),
  ).toEqual({
    ...DEFAULT_INFRASTRUCTURE,
    imageCdnHostname: 'cdn.example.com',
  })
  expect(
    readInfrastructureSettings({
      plcDirectoryHostname: 'http://bad.example/path',
      useLinkRedirects: false,
    }),
  ).toEqual({
    ...DEFAULT_INFRASTRUCTURE,
    useLinkRedirects: false,
  })
  expect(
    readInfrastructureSettings({
      plcDirectoryHostname: 'https://plc.eurosky.network/',
    }).plcDirectoryHostname,
  ).toBe('plc.eurosky.network')
  expect(
    PLC_DIRECTORY_PRESETS.map(preset => plcDirectoryOrigin(preset.hostname)),
  ).toEqual([
    'https://plc.directory',
    'https://plc.eurosky.network',
    'https://plc.wafflehouse.dev',
  ])
})

it.each([
  'http://api.example.com',
  'https://',
  'api.example.com/path',
  'user@api.example.com',
  'api.example.com:443',
  'api.example.com?q=x',
  'api.example.com#x',
  'api.example.com\\path',
  'api. example.com',
  '-bad.example',
  'bad..example',
  'localhost',
  'a'.repeat(64) + '.com',
])('rejects malformed or ambiguous host %s', value => {
  expect(() => normalizeHostname(value)).toThrow()
})

it('changes only Bluesky image hosts while retaining format, preset, query and CID', () => {
  for (const preset of [
    'feed_thumbnail',
    'feed_fullsize',
    'avatar_thumbnail',
    'avatar',
    'banner',
    'download',
  ]) {
    expect(
      rewriteImageUrl(
        `https://cdn.bsky.app/img/${preset}/plain/did:plc:abc/cid@jpeg?x=1`,
        'cdn.example.com',
      ),
    ).toBe(
      `https://cdn.example.com/img/${preset}/plain/did:plc:abc/cid@jpeg?x=1`,
    )
  }
  for (const uri of [
    'file:///tmp/image.jpg',
    'data:image/png;base64,abc',
    'https://elsewhere.example/img/feed_fullsize/plain/cid',
    'https://cdn.bsky.app.evil.example/img/avatar/plain/cid',
    'https://cdn.bsky.app/img/avatar/plain@cid',
    'https://video.bsky.app/watch/cid',
  ]) {
    expect(rewriteImageUrl(uri, 'cdn.example.com')).toBe(uri)
  }
})

it('rewrites source arrays and placeholders without mutating image metadata or local refs', () => {
  const source = {
    uri: 'https://cdn.bsky.app/img/avatar/plain/did/cid@jpeg',
    width: 100,
    height: 100,
    cacheKey: 'avatar',
    headers: {Accept: 'image/*'},
  }
  const result = rewriteImageSource(source, 'cdn.example.com')
  expect(result).toEqual({
    ...source,
    uri: 'https://cdn.example.com/img/avatar/plain/did/cid@jpeg',
  })
  expect(source.uri).toContain('cdn.bsky.app')
  expect(
    rewriteImageSource([source.uri, 'file:///a.jpg'], 'cdn.example.com'),
  ).toEqual([result.uri, 'file:///a.jpg'])
  const placeholder = {blurhash: 'abc'}
  expect(rewriteImageSource(placeholder, 'cdn.example.com')).toBe(placeholder)
  expect(rewriteImageSource(42, 'cdn.example.com')).toBe(42)
  expect(rewriteImageSource(source, '')).toBe(source)
})

it('rejects caches from another AppView and preserves the existing default cache buster', () => {
  expect(appviewCacheBuster('1.0', '')).toBe('1.0')
  expect(appviewCacheBuster('1.0', 'api.eurosky.network')).not.toBe(
    appviewCacheBuster('1.0', 'api.blacksky.community'),
  )
  expect(appviewCacheBuster('1.0', 'api.eurosky.network')).not.toBe('1.0')
})
