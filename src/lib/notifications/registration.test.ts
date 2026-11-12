import type * as Registration from '#/lib/notifications/registration'

jest.mock('react-native', () => ({Platform: {OS: 'android'}}))
jest.mock('#/env', () => ({
  IS_BLUESKY_PLUS: true,
  PUSH_GATEWAY_DID: 'did:web:push.noz.am',
}))
jest.mock('#/lib/constants', () => ({
  PUBLIC_APPVIEW_DID: 'did:web:api.bsky.app',
  PUBLIC_STAGING_APPVIEW_DID: 'did:web:api.staging.bsky.dev',
}))

function loadRegistration({
  gateway = 'did:web:push.noz.am',
  platform = 'android',
  caelum = true,
}: {
  gateway?: string
  platform?: string
  caelum?: boolean
} = {}) {
  jest.resetModules()
  jest.doMock('react-native', () => ({Platform: {OS: platform}}))
  jest.doMock('#/env', () => ({
    IS_BLUESKY_PLUS: caelum,
    PUSH_GATEWAY_DID: gateway || undefined,
  }))
  return require('#/lib/notifications/registration') as typeof Registration
}

it('enables configured Caelum Android push', () => {
  expect(loadRegistration().PUSH_ENABLED).toBe(true)
})

it('keeps Caelum push disabled without a gateway', () => {
  expect(loadRegistration({gateway: ''}).PUSH_ENABLED).toBe(false)
})

it.each(['ios', 'web'])('keeps Caelum push disabled on %s', platform => {
  expect(loadRegistration({platform}).PUSH_ENABLED).toBe(false)
})

it('uses our gateway and Android app ID for production and staging accounts', () => {
  const {getPushRegistration} = loadRegistration()
  for (const service of ['https://bsky.social', 'https://staging.bsky.dev']) {
    expect(getPushRegistration(service)).toEqual({
      serviceDid: 'did:web:push.noz.am',
      appId: 'am.noz.caelum',
    })
  }
})

it('preserves upstream production and staging registration defaults', () => {
  const {getPushRegistration, PUSH_ENABLED} = loadRegistration({
    caelum: false,
    gateway: '',
  })
  expect(PUSH_ENABLED).toBe(true)
  expect(getPushRegistration('https://bsky.social')).toEqual({
    serviceDid: 'did:web:api.bsky.app',
    appId: 'xyz.blueskyweb.app',
  })
  expect(getPushRegistration('https://staging.bsky.dev')).toEqual({
    serviceDid: 'did:web:api.staging.bsky.dev',
    appId: 'xyz.blueskyweb.app',
  })
})
