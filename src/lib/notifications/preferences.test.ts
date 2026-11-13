import BackgroundNotificationHandler from '@bsky.app/expo-background-notification-handler'
import {beforeEach, describe, expect, it, jest} from '@jest/globals'
import {act, renderHook} from '@testing-library/react-native'

import {
  type AppNotificationPreferences,
  clearPushGatewaySyncCache,
  getDisabledPushReasons,
  isNotificationReasonPushDisabled,
  syncDisabledPushReasonsNative,
  syncPreferencesToPushGateway,
  useSyncEffectForNotificationPreferences,
} from '#/lib/notifications/preferences'
import {app} from '#/lexicons'

jest.mock('@bsky.app/expo-background-notification-handler', () => ({
  __esModule: true,
  default: {
    setDisabledPushReasonsAsync: jest.fn(() => Promise.resolve()),
  },
}))

jest.mock('#/env', () => ({
  IS_NATIVE: true,
  PUSH_GATEWAY_DID: 'did:web:push.noz.am',
}))

jest.mock('#/lib/constants', () => ({
  NOTIF_SERVICE: 'did:web:push.noz.am#bsky_notif',
  PUBLIC_APPVIEW_DID: 'did:web:api.bsky.app',
}))

jest.mock('#/lib/notifications/registration', () => ({
  PUSH_ENABLED: true,
  getPushRegistration: () => ({
    serviceDid: 'did:web:push.noz.am',
    appId: 'am.noz.caelum',
  }),
}))

jest.mock('#/lib/notifications/util', () => ({
  logger: {debug: jest.fn(), warn: jest.fn()},
}))

function makePrefs(
  overrides: Partial<AppNotificationPreferences> = {},
): AppNotificationPreferences {
  return {
    follow: {include: 'all', list: true, push: true},
    like: {include: 'all', list: true, push: true},
    likeViaRepost: {include: 'all', list: true, push: true},
    mention: {include: 'all', list: true, push: true},
    quote: {include: 'all', list: true, push: true},
    reply: {include: 'all', list: true, push: true},
    repost: {include: 'all', list: true, push: true},
    repostViaRepost: {include: 'all', list: true, push: true},
    starterpackJoined: {list: true, push: true},
    subscribedPost: {list: true, push: true},
    unverified: {list: true, push: true},
    verified: {list: true, push: true},
    ...overrides,
  }
}

describe('notification push preferences helpers', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    clearPushGatewaySyncCache()
  })

  it('extracts sorted wire reasons for disabled push categories', () => {
    const prefs = makePrefs({
      like: {include: 'all', list: true, push: false},
      likeViaRepost: {include: 'all', list: true, push: false},
      subscribedPost: {list: true, push: false},
    })

    expect(getDisabledPushReasons(prefs)).toEqual([
      'like',
      'like-via-repost',
      'subscribed-post',
    ])
  })

  it('checks whether a wire reason has push disabled', () => {
    const prefs = makePrefs({
      like: {include: 'all', list: true, push: false},
      repostViaRepost: {include: 'all', list: true, push: false},
    })

    expect(isNotificationReasonPushDisabled(prefs, 'like')).toBe(true)
    expect(isNotificationReasonPushDisabled(prefs, 'repost-via-repost')).toBe(
      true,
    )
    expect(isNotificationReasonPushDisabled(prefs, 'reply')).toBe(false)
    expect(isNotificationReasonPushDisabled(prefs, 'unknown-reason')).toBe(
      false,
    )
  })

  it('syncs disabled reasons to the native background handler', async () => {
    await syncDisabledPushReasonsNative(
      'did:plc:alice',
      makePrefs({like: {include: 'all', list: true, push: false}}),
    )

    expect(
      BackgroundNotificationHandler.setDisabledPushReasonsAsync,
    ).toHaveBeenCalledWith('did:plc:alice', ['like'])
  })

  it('syncs preferences to the push gateway and deduplicates unchanged state', async () => {
    const call = jest.fn<(...args: unknown[]) => Promise<unknown>>(() =>
      Promise.resolve({preferences: {}}),
    )
    const client = {call} as unknown as Parameters<
      typeof syncPreferencesToPushGateway
    >[0]['client']
    const prefs = makePrefs({
      like: {include: 'all', list: true, push: false},
    })

    await syncPreferencesToPushGateway({
      client,
      did: 'did:plc:alice',
      preferences: prefs,
    })
    await syncPreferencesToPushGateway({
      client,
      did: 'did:plc:alice',
      preferences: prefs,
    })

    expect(call).toHaveBeenCalledTimes(1)
    expect(call).toHaveBeenCalledWith(
      app.bsky.notification.putPreferencesV2,
      expect.objectContaining({
        like: {include: 'all', list: true, push: false},
      }),
      {service: 'did:web:push.noz.am#bsky_notif'},
    )

    await syncPreferencesToPushGateway({
      client,
      did: 'did:plc:alice',
      preferences: prefs,
      force: true,
    })
    expect(call).toHaveBeenCalledTimes(2)
  })

  it('syncs both native and gateway preferences from the effect hook', async () => {
    const call = jest.fn<(...args: unknown[]) => Promise<unknown>>(() =>
      Promise.resolve({preferences: {}}),
    )
    const pdsClient = {call} as unknown as Parameters<
      typeof useSyncEffectForNotificationPreferences
    >[0]['pdsClient']
    const prefs = makePrefs({
      like: {include: 'all', list: true, push: false},
    })

    renderHook(() =>
      useSyncEffectForNotificationPreferences({
        did: 'did:plc:alice',
        preferences: prefs,
        pdsClient,
      }),
    )

    await act(async () => {
      await Promise.resolve()
    })

    expect(
      BackgroundNotificationHandler.setDisabledPushReasonsAsync,
    ).toHaveBeenCalledWith('did:plc:alice', ['like'])
    expect(call).toHaveBeenCalledTimes(1)
  })
})
