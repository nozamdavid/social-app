import {type Agent} from '@atproto/lex'
import {PasswordSession} from '@atproto/lex-password-session'

import {BLUESKY_PROXY_HEADER, CHAT_PROXY_SERVICE} from '#/lib/constants'
import {createLexClient} from '#/lib/lexClient'
import {createProxiedUrl} from '#/lib/strings/url-helpers'
import {
  asFetch,
  DID,
  HANDLE,
  json,
  makeAccount,
  makeDidDoc,
  makeMockFetch,
  PDS_HOST,
  urlsOf,
} from '#/state/session/__tests__/mock-fetch'
import {buildAppviewClient} from '#/state/session/clients'
import {sessionAccountToSessionData} from '#/state/session/session-data'
import {app} from '#/lexicons'
import {DEFAULT_INFRASTRUCTURE} from './config'
import {isAppviewRequest, withInfrastructureRouting} from './routing'

let mockSettings = {...DEFAULT_INFRASTRUCTURE}
jest.mock('#/features/infrastructure/preferences', () => ({
  getInfrastructureSettings: () => mockSettings,
}))
jest.mock('#/analytics/identifiers', () => ({
  getDeviceId: () => 'device-id',
  getSessionId: () => 'session-id',
}))
jest.mock('jwt-decode', () => ({
  jwtDecode: () => ({scope: 'com.atproto.access'}),
}))

const profile = {did: 'did:plc:test', handle: 'test.example'}
function response() {
  return new Response(JSON.stringify(profile), {
    status: 200,
    headers: {'Content-Type': 'application/json'},
  })
}

function requestUrl(input: RequestInfo | URL) {
  return new URL(
    typeof input === 'string'
      ? input
      : input instanceof URL
        ? input.href
        : input.url,
  )
}

beforeEach(() => {
  mockSettings = {...DEFAULT_INFRASTRUCTURE}
})

it('updates an existing authenticated client proxy without changing its PDS or losing headers', async () => {
  const fetchHandler = jest.fn((_path: string, _init: RequestInit) =>
    Promise.resolve(response()),
  )
  const agent: Agent = {did: 'did:plc:test', fetchHandler}
  const client = createLexClient(agent, {
    service: BLUESKY_PROXY_HEADER.get(),
    includeDeviceSessionHeaders: true,
  })
  mockSettings.appviewHostname = 'api.eurosky.network'
  await client.call(
    app.bsky.actor.getProfile,
    {actor: 'test.example'},
    {
      headers: {
        'atproto-accept-labelers': 'did:plc:labeler',
        'accept-language': 'es',
      },
    },
  )
  expect(fetchHandler.mock.calls[0][0]).toMatch(
    /^\/xrpc\/app.bsky.actor.getProfile\?/,
  )
  const headers = new Headers(fetchHandler.mock.calls[0][1].headers)
  expect(headers.get('atproto-proxy')).toBe(
    'did:web:api.eurosky.network#bsky_appview',
  )
  expect(headers.get('atproto-accept-labelers')).toContain('did:plc:labeler')
  expect(headers.get('accept-language')).toBe('es')
  expect(headers.has('x-atproto-device-id')).toBe(false)
  mockSettings.appviewHostname = ''
  await client.call(app.bsky.actor.getProfile, {actor: 'test.example'})
  expect(
    new Headers(fetchHandler.mock.calls[1][1].headers).get('atproto-proxy'),
  ).toBe(BLUESKY_PROXY_HEADER.get())
})

it('routes existing public clients, including the direct language-sensitive feed client', async () => {
  for (const host of ['public.api.bsky.app', 'api.bsky.app']) {
    const fetchMock = jest.fn((_url: RequestInfo | URL, _init?: RequestInit) =>
      Promise.resolve(response()),
    )
    const client = createLexClient(
      {service: `https://${host}`, fetch: fetchMock},
      {includeDeviceSessionHeaders: true},
    )
    mockSettings.appviewHostname = 'api.blacksky.community'
    await client.call(app.bsky.actor.getProfile, {actor: 'test.example'})
    expect(requestUrl(fetchMock.mock.calls[0][0]).href).toContain(
      'https://api.blacksky.community/xrpc/',
    )
    expect(
      new Headers(fetchMock.mock.calls[0][1]?.headers).has('authorization'),
    ).toBe(false)
    expect(
      new Headers(fetchMock.mock.calls[0][1]?.headers).has(
        'x-atproto-device-id',
      ),
    ).toBe(false)
  }
})

it('keeps session authorization on the PDS when choosing another AppView', async () => {
  mockSettings.appviewHostname = 'api.eurosky.network'
  const fetchMock = makeMockFetch({
    'app.bsky.actor.getProfile': () => json({did: DID, handle: HANDLE}),
  })
  const session = new PasswordSession(
    {
      ...sessionAccountToSessionData(makeAccount()),
      didDoc: makeDidDoc(PDS_HOST),
    },
    {fetch: asFetch(fetchMock)},
  )
  const client = buildAppviewClient(session)
  await client.call(app.bsky.actor.getProfile, {actor: HANDLE})
  expect(new URL(urlsOf(fetchMock)[0]).origin).toBe(PDS_HOST)
  const headers = new Headers(fetchMock.mock.calls[0][1]?.headers)
  expect(headers.has('authorization')).toBe(true)
  expect(headers.get('atproto-proxy')).toBe(
    'did:web:api.eurosky.network#bsky_appview',
  )
})

it.each([
  'com.atproto.server.refreshSession',
  'com.atproto.repo.putRecord',
  'com.atproto.repo.uploadBlob',
  'app.bsky.actor.getPreferences',
  'app.bsky.actor.putPreferences',
  'app.bsky.video.uploadVideo',
  'chat.bsky.convo.getConvo',
  'tools.ozone.moderation.emitEvent',
])('leaves %s on its existing service', async nsid => {
  mockSettings.appviewHostname = 'api.eurosky.network'
  const fetchHandler = jest.fn((_path: string, _init: RequestInit) =>
    Promise.resolve(response()),
  )
  const agent = withInfrastructureRouting({did: 'did:plc:test', fetchHandler})
  const init = {
    method: 'POST',
    headers: {'atproto-proxy': BLUESKY_PROXY_HEADER.get()},
  }
  await agent.fetchHandler(`/xrpc/${nsid}`, init)
  expect(fetchHandler).toHaveBeenCalledWith(`/xrpc/${nsid}`, init)
  expect(isAppviewRequest(`/xrpc/${nsid}`)).toBe(false)
})

it('preserves explicit chat/labeler proxy targets even on AppView-shaped paths', async () => {
  mockSettings.appviewHostname = 'api.eurosky.network'
  const fetchHandler = jest.fn((_path: string, _init: RequestInit) =>
    Promise.resolve(response()),
  )
  const agent = withInfrastructureRouting({did: 'did:plc:test', fetchHandler})
  for (const proxy of [CHAT_PROXY_SERVICE, 'did:plc:other#atproto_labeler']) {
    await agent.fetchHandler('/xrpc/app.bsky.actor.getProfile', {
      headers: {'atproto-proxy': proxy},
    })
    expect(
      new Headers(fetchHandler.mock.calls.at(-1)![1].headers).get(
        'atproto-proxy',
      ),
    ).toBe(proxy)
  }
})

it('never redirects credential-bearing public requests or calls to unrelated services', async () => {
  mockSettings.appviewHostname = 'api.eurosky.network'
  const configurations: Array<{service: string; headers?: HeadersInit}> = [
    {service: 'https://api.bsky.app', headers: {Authorization: 'Bearer test'}},
    {service: 'https://public.api.bsky.app', headers: {DPoP: 'proof'}},
    {service: 'https://custom-pds.example'},
  ]
  for (const options of configurations) {
    const fetchMock = jest.fn((_url: RequestInfo | URL) =>
      Promise.resolve(response()),
    )
    const agent = withInfrastructureRouting({...options, fetch: fetchMock})
    await agent.fetchHandler('/xrpc/app.bsky.actor.getProfile', {})
    expect(requestUrl(fetchMock.mock.calls[0][0]).origin).toBe(options.service)
  }
})

it('honors the redirect preference at click time and leaves non-web schemes alone', () => {
  const target = 'https://example.com/path?q=hello%20world'
  expect(createProxiedUrl(target)).toBe(
    `https://go.bsky.app/redirect?u=${encodeURIComponent(target)}`,
  )
  mockSettings.useLinkRedirects = false
  expect(createProxiedUrl(target)).toBe(target)
  mockSettings.useLinkRedirects = true
  expect(createProxiedUrl('mailto:hello@example.com')).toBe(
    'mailto:hello@example.com',
  )
  expect(createProxiedUrl('not a URL')).toBe('not a URL')
})
