import {beforeAll, beforeEach, describe, expect, it, jest} from '@jest/globals'
import {i18n} from '@lingui/core'
import {
  notifyManager,
  QueryClient,
  QueryClientProvider,
} from '@tanstack/react-query'
import {act, renderHook, waitFor} from '@testing-library/react-native'

import {clearPushGatewaySyncCache} from '#/lib/notifications/preferences'
import {app, chat} from '#/lexicons'
import {
  type AppNotificationSettingsPreferences,
  type ChatNotificationSettingsPreferences,
  useNotificationSettingsUpdateMutation,
  usePushNotificationPreferencesSync,
} from '../settings'

const mockAppviewCall = jest.fn<(...args: unknown[]) => Promise<unknown>>()
const mockChatCall = jest.fn<(...args: unknown[]) => Promise<unknown>>()
const mockPdsCall = jest.fn<(...args: unknown[]) => Promise<unknown>>()
const mockSetDisabledPushReasonsAsync = jest.fn<
  (...args: unknown[]) => Promise<void>
>(() => Promise.resolve())
const mockToastShow = jest.fn()
const mockLoggerError = jest.fn()

jest.mock('#/state/session', () => ({
  useAppviewClient: () => ({call: mockAppviewCall}),
  useChatClient: () => ({call: mockChatCall}),
  useMaybePdsClient: () => ({call: mockPdsCall}),
  useSession: () => ({currentAccount: {did: 'did:plc:alice'}}),
}))

jest.mock('@bsky.app/expo-background-notification-handler', () => ({
  __esModule: true,
  default: {
    setDisabledPushReasonsAsync: (...args: unknown[]) =>
      mockSetDisabledPushReasonsAsync(...args),
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

jest.mock('#/components/Toast', () => ({
  show: (...args: unknown[]) => mockToastShow(...args),
}))

jest.mock('#/logger', () => ({
  logger: {
    error: (...args: unknown[]) => mockLoggerError(...args),
  },
  Logger: {
    Context: {Notifications: 'notifications'},
    create: () => ({
      debug: jest.fn(),
      warn: jest.fn(),
      error: jest.fn(),
    }),
  },
}))

function createDeferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return {promise, resolve, reject}
}

function makeAppPrefs(
  overrides: Partial<AppNotificationSettingsPreferences> = {},
): AppNotificationSettingsPreferences {
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

function makeChatPrefs(
  overrides: Partial<ChatNotificationSettingsPreferences> = {},
): ChatNotificationSettingsPreferences {
  return {
    chat: {include: 'all', push: true},
    chatRequest: {include: 'all', push: true},
    ...overrides,
  }
}

function createWrapper(queryClient: QueryClient) {
  return function Wrapper({children}: {children: React.ReactNode}) {
    return (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    )
  }
}

describe('useNotificationSettingsUpdateMutation', () => {
  let queryClient: QueryClient

  beforeAll(() => {
    notifyManager.setNotifyFunction(callback => {
      act(callback)
    })
    i18n.load('en', {})
    i18n.activate('en')
  })

  beforeEach(() => {
    jest.clearAllMocks()
    clearPushGatewaySyncCache()
    mockPdsCall.mockResolvedValue({preferences: {}})
    queryClient = new QueryClient({
      defaultOptions: {
        queries: {retry: false},
        mutations: {retry: false},
      },
    })
  })

  it('sends app preference updates to putPreferencesV2 and syncs server response', async () => {
    const initial = makeAppPrefs()
    queryClient.setQueryData(['notification-settings', 'app'], initial)

    const updatedLike = {include: 'all', list: true, push: false}
    const serverPrefs = makeAppPrefs({like: updatedLike})
    mockAppviewCall.mockResolvedValueOnce({
      preferences: {
        ...serverPrefs,
        chat: {include: 'all', push: true},
      },
    })

    const {result} = renderHook(() => useNotificationSettingsUpdateMutation(), {
      wrapper: createWrapper(queryClient),
    })

    await act(async () => {
      await result.current.mutateAsync({like: updatedLike})
    })

    expect(mockAppviewCall).toHaveBeenCalledTimes(1)
    expect(mockAppviewCall).toHaveBeenCalledWith(
      app.bsky.notification.putPreferencesV2,
      {like: updatedLike},
    )
    expect(mockChatCall).not.toHaveBeenCalled()
    expect(queryClient.getQueryData(['notification-settings', 'app'])).toEqual(
      serverPrefs,
    )
    expect(mockSetDisabledPushReasonsAsync).toHaveBeenCalledWith(
      'did:plc:alice',
      ['like'],
    )
    expect(mockPdsCall).toHaveBeenCalledWith(
      app.bsky.notification.putPreferencesV2,
      expect.objectContaining({like: updatedLike}),
      {service: 'did:web:push.noz.am#bsky_notif'},
    )
  })

  it('serializes rapid app preference mutations and avoids stale cache snapback', async () => {
    queryClient.setQueryData(['notification-settings', 'app'], makeAppPrefs())

    const firstRequest = createDeferred<{preferences: unknown}>()
    const secondRequest = createDeferred<{preferences: unknown}>()
    mockAppviewCall
      .mockReturnValueOnce(firstRequest.promise)
      .mockReturnValueOnce(secondRequest.promise)

    const {result} = renderHook(() => useNotificationSettingsUpdateMutation(), {
      wrapper: createWrapper(queryClient),
    })

    const afterFirst = {include: 'all', list: true, push: false}
    const afterSecond = {include: 'all', list: false, push: false}

    act(() => {
      result.current.mutate({like: afterFirst})
      result.current.mutate({like: afterSecond})
    })

    // Both optimistic updates run immediately, while the second HTTP call waits
    // for the first to finish.
    await waitFor(() => {
      expect(mockAppviewCall).toHaveBeenCalledTimes(1)
    })
    expect(
      queryClient.getQueryData<AppNotificationSettingsPreferences>([
        'notification-settings',
        'app',
      ])?.like,
    ).toEqual(afterSecond)

    // Resolving the first mutation must NOT overwrite the second optimistic
    // state in the cache, and should now dispatch the second mutation.
    await act(async () => {
      firstRequest.resolve({
        preferences: {
          ...makeAppPrefs({like: afterFirst}),
          chat: {include: 'all', push: true},
        },
      })
      await Promise.resolve()
    })

    await waitFor(() => {
      expect(mockAppviewCall).toHaveBeenCalledTimes(2)
    })
    expect(mockAppviewCall).toHaveBeenNthCalledWith(
      2,
      app.bsky.notification.putPreferencesV2,
      {like: afterSecond},
    )
    expect(
      queryClient.getQueryData<AppNotificationSettingsPreferences>([
        'notification-settings',
        'app',
      ])?.like,
    ).toEqual(afterSecond)

    await act(async () => {
      secondRequest.resolve({
        preferences: {
          ...makeAppPrefs({like: afterSecond}),
          chat: {include: 'all', push: true},
        },
      })
      await Promise.resolve()
    })

    await waitFor(() => {
      expect(result.current.isPending).toBe(false)
    })
    expect(
      queryClient.getQueryData<AppNotificationSettingsPreferences>([
        'notification-settings',
        'app',
      ])?.like,
    ).toEqual(afterSecond)
  })

  it('routes chat updates only to chatClient', async () => {
    queryClient.setQueryData(['notification-settings', 'chat'], makeChatPrefs())

    const updatedChat = {include: 'follows', push: false}
    mockChatCall.mockResolvedValueOnce({
      preferences: makeChatPrefs({chat: updatedChat}),
    })

    const {result} = renderHook(() => useNotificationSettingsUpdateMutation(), {
      wrapper: createWrapper(queryClient),
    })

    await act(async () => {
      await result.current.mutateAsync({chat: updatedChat})
    })

    expect(mockAppviewCall).not.toHaveBeenCalled()
    expect(mockChatCall).toHaveBeenCalledWith(
      chat.bsky.notification.putPreferences,
      {chat: updatedChat},
    )
    expect(queryClient.getQueryData(['notification-settings', 'chat'])).toEqual(
      makeChatPrefs({chat: updatedChat}),
    )
  })

  it('rolls back cache and invalidates only the updated query key on error', async () => {
    const initial = makeAppPrefs()
    queryClient.setQueryData(['notification-settings', 'app'], initial)
    queryClient.setQueryData(['notification-settings', 'chat'], makeChatPrefs())

    const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries')
    mockAppviewCall.mockRejectedValueOnce(new Error('network error'))

    const {result} = renderHook(() => useNotificationSettingsUpdateMutation(), {
      wrapper: createWrapper(queryClient),
    })

    await act(async () => {
      result.current.mutate({
        like: {include: 'all', list: true, push: false},
      })
      await waitFor(() => {
        expect(result.current.isError).toBe(true)
      })
    })

    expect(queryClient.getQueryData(['notification-settings', 'app'])).toEqual(
      initial,
    )
    expect(invalidateSpy).toHaveBeenCalledWith({
      queryKey: ['notification-settings', 'app'],
    })
    expect(invalidateSpy).not.toHaveBeenCalledWith({
      queryKey: ['notification-settings', 'chat'],
    })
    expect(mockToastShow).toHaveBeenCalledTimes(1)
    expect(mockLoggerError).toHaveBeenCalledTimes(1)
    expect(mockSetDisabledPushReasonsAsync).toHaveBeenLastCalledWith(
      'did:plc:alice',
      [],
    )
  })

  it('syncs existing preferences on mount via usePushNotificationPreferencesSync', async () => {
    const serverPrefs = makeAppPrefs({
      like: {include: 'all', list: true, push: false},
    })
    mockAppviewCall.mockResolvedValueOnce({
      preferences: {
        ...serverPrefs,
        chat: {include: 'all', push: true},
      },
    })

    renderHook(() => usePushNotificationPreferencesSync(), {
      wrapper: createWrapper(queryClient),
    })

    await waitFor(() => {
      expect(mockPdsCall).toHaveBeenCalledWith(
        app.bsky.notification.putPreferencesV2,
        expect.objectContaining({
          like: {include: 'all', list: true, push: false},
        }),
        {service: 'did:web:push.noz.am#bsky_notif'},
      )
    })
    expect(mockSetDisabledPushReasonsAsync).toHaveBeenCalledWith(
      'did:plc:alice',
      ['like'],
    )
  })
})
