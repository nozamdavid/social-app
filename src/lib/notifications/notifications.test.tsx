import {Platform} from 'react-native'
import * as Notifications from 'expo-notifications'
import {act, renderHook} from '@testing-library/react-native'

import {
  unregisterPushToken,
  useGetAndRegisterPushToken,
  useNotificationsRegistration,
} from '#/lib/notifications/notifications'
import {usePdsClient} from '#/state/session'

jest.mock('expo-notifications', () => ({
  getPermissionsAsync: jest.fn(),
  getDevicePushTokenAsync: jest.fn(),
  addPushTokenListener: jest.fn(),
}))

jest.mock('#/env', () => ({
  IS_NATIVE: true,
  IS_DEV: true,
  IS_BLUESKY_PLUS: true,
}))
jest.mock('#/lib/constants', () => ({
  NOTIF_SERVICE: 'did:web:push.noz.am#bsky_notif',
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
jest.mock('#/state/session', () => ({
  useSession: () => ({currentAccount: {service: 'https://bsky.social'}}),
  usePdsClient: jest.fn(),
}))
jest.mock('#/ageAssurance', () => ({
  useAgeAssurance: () => ({state: {access: 'full'}, Access: {Full: 'full'}}),
}))
jest.mock('#/analytics', () => ({useAnalytics: () => ({metric: jest.fn()})}))
jest.mock('@bsky.app/expo-background-notification-handler', () => ({}))

const call = jest.fn().mockResolvedValue({})
const token = {type: 'android', data: 'test-device-token'} as const

beforeEach(() => {
  jest.useFakeTimers()
  jest.replaceProperty(Platform, 'OS', 'android')
  jest.clearAllMocks()
  jest
    .mocked(usePdsClient)
    .mockReturnValue({call} as unknown as ReturnType<typeof usePdsClient>)
  jest.spyOn(Notifications, 'getPermissionsAsync').mockResolvedValue({
    granted: true,
  } as Notifications.NotificationPermissionsStatus)
  jest.spyOn(Notifications, 'getDevicePushTokenAsync').mockResolvedValue(token)
})

afterEach(() => {
  jest.clearAllTimers()
  jest.useRealTimers()
  jest.restoreAllMocks()
})

it('registers the native token with our DID and proxy even in Android development', async () => {
  const {result} = renderHook(() => useGetAndRegisterPushToken())
  await act(async () => {
    await result.current()
    jest.advanceTimersByTime(101)
  })
  expect(call).toHaveBeenCalledWith(
    expect.anything(),
    expect.objectContaining({
      platform: 'android',
      token: token.data,
      serviceDid: 'did:web:push.noz.am',
      appId: 'am.noz.caelum',
    }),
    {service: 'did:web:push.noz.am#bsky_notif'},
  )
})

it('registers refreshed tokens and removes the listener on unmount', async () => {
  const remove = jest.fn()
  const listener = jest
    .spyOn(Notifications, 'addPushTokenListener')
    .mockReturnValue({remove})
  const {unmount} = renderHook(() => useNotificationsRegistration())
  await act(() => Promise.resolve())
  const refresh = listener.mock.calls[0][0]
  act(() => {
    refresh({...token, data: 'rotated-device-token'})
    jest.advanceTimersByTime(101)
  })
  expect(call).toHaveBeenLastCalledWith(
    expect.anything(),
    expect.objectContaining({token: 'rotated-device-token'}),
    {service: 'did:web:push.noz.am#bsky_notif'},
  )
  unmount()
  expect(remove).toHaveBeenCalled()
})

it('unregisters through the same gateway on logout', async () => {
  await unregisterPushToken([
    {
      client: {call} as unknown as Parameters<
        typeof unregisterPushToken
      >[0][number]['client'],
      service: 'https://bsky.social',
      handle: 'test.example',
    },
  ])
  expect(call).toHaveBeenCalledWith(
    expect.anything(),
    expect.objectContaining({
      platform: 'android',
      token: token.data,
      serviceDid: 'did:web:push.noz.am',
      appId: 'am.noz.caelum',
    }),
    {service: 'did:web:push.noz.am#bsky_notif'},
  )
})
