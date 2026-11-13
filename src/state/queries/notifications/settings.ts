import {t} from '@lingui/core/macro'
import {
  type QueryClient,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'

import {
  syncDisabledPushReasonsNative,
  syncPreferencesToPushGateway,
  useSyncEffectForNotificationPreferences,
} from '#/lib/notifications/preferences'
import {PUSH_ENABLED} from '#/lib/notifications/registration'
import {logger} from '#/logger'
import {
  useAppviewClient,
  useChatClient,
  useMaybePdsClient,
  useSession,
} from '#/state/session'
import * as Toast from '#/components/Toast'
import {IS_NATIVE} from '#/env'
import {app, chat} from '#/lexicons'

const RQKEY_ROOT = 'notification-settings'
export const RQKEY_APP = [RQKEY_ROOT, 'app']
const RQKEY_CHAT = [RQKEY_ROOT, 'chat']
const MUTATION_KEY = [RQKEY_ROOT]

// App notification preferences live on the appview. Chat preferences live on a
// separate chat service proxy that can be up or down independently, so they are
// fetched and cached separately. This combined type names every preference for
// the generic settings dialog, but it is never the shape of a query response.
export type NotificationSettingsPreferences = Omit<
  app.bsky.notification.defs.Preferences,
  'chat'
> &
  Partial<Pick<chat.bsky.notification.defs.Preferences, 'chat' | 'chatRequest'>>

export type AppNotificationSettingsPreferences = Omit<
  app.bsky.notification.defs.Preferences,
  'chat'
>

export type ChatNotificationSettingsPreferences = Pick<
  chat.bsky.notification.defs.Preferences,
  'chat' | 'chatRequest'
>

export type NotificationSettingsPreferenceName = Exclude<
  keyof NotificationSettingsPreferences,
  '$type'
>

export type NotificationSettingsPreference =
  | app.bsky.notification.defs.Preference
  | app.bsky.notification.defs.FilterablePreference
  | chat.bsky.notification.defs.ChatPreference

export function isChatPreferenceName(
  name: NotificationSettingsPreferenceName,
): name is 'chat' | 'chatRequest' {
  return name === 'chat' || name === 'chatRequest'
}

type NotificationSettingsUpdate = Partial<NotificationSettingsPreferences>

type AppNotificationSettingsUpdate = Partial<
  Omit<app.bsky.notification.defs.Preferences, '$type' | 'chat'>
>

type ChatNotificationSettingsUpdate =
  Partial<ChatNotificationSettingsPreferences>

export function useNotificationSettingsQuery({
  enabled,
}: {enabled?: boolean} = {}) {
  const client = useAppviewClient()

  return useQuery({
    queryKey: RQKEY_APP,
    queryFn: async (): Promise<AppNotificationSettingsPreferences> => {
      const data = await client.call(app.bsky.notification.getPreferences)
      return appPreferencesWithoutChat(data.preferences)
    },
    enabled,
  })
}

export function useChatNotificationSettingsQuery({
  enabled,
}: {enabled?: boolean} = {}) {
  const client = useChatClient()

  return useQuery({
    queryKey: RQKEY_CHAT,
    queryFn: async (): Promise<ChatNotificationSettingsPreferences> => {
      const data = await client.call(chat.bsky.notification.getPreferences)
      return chatPreferencesForSettings(data.preferences)
    },
    enabled,
  })
}
export function usePushNotificationPreferencesSync() {
  const {currentAccount} = useSession()
  const pdsClient = useMaybePdsClient()
  const {data} = useNotificationSettingsQuery({
    enabled: Boolean(currentAccount && IS_NATIVE && PUSH_ENABLED),
  })

  useSyncEffectForNotificationPreferences({
    did: currentAccount?.did,
    preferences: data,
    pdsClient,
  })
}

export function useNotificationSettingsUpdateMutation() {
  /*
   * App preferences live on the appview and chat preferences on the chat
   * service, so a combined update fans out over both clients.
   */
  const appviewClient = useAppviewClient()
  const chatClient = useChatClient()
  const pdsClient = useMaybePdsClient()
  const {currentAccount} = useSession()
  const queryClient = useQueryClient()

  return useMutation({
    mutationKey: MUTATION_KEY,
    scope: {id: RQKEY_ROOT},
    mutationFn: async (update: NotificationSettingsUpdate) => {
      const {appUpdate, chatUpdate} = splitNotificationSettingsUpdate(update)
      const [appRes, chatRes] = await Promise.all([
        hasUpdates(appUpdate)
          ? appviewClient.call(
              app.bsky.notification.putPreferencesV2,
              appUpdate,
            )
          : undefined,
        hasUpdates(chatUpdate)
          ? chatClient.call(chat.bsky.notification.putPreferences, chatUpdate)
          : undefined,
      ])
      return {
        appPreferences: appRes
          ? appPreferencesWithoutChat(appRes.preferences)
          : undefined,
        chatPreferences: chatRes
          ? chatPreferencesForSettings(chatRes.preferences)
          : undefined,
      }
    },
    onMutate: async update => {
      const {appUpdate, chatUpdate} = splitNotificationSettingsUpdate(update)
      const hasApp = hasUpdates(appUpdate)
      const hasChat = hasUpdates(chatUpdate)
      const previousApp = hasApp
        ? queryClient.getQueryData<AppNotificationSettingsPreferences>(
            RQKEY_APP,
          )
        : undefined
      const previousChat = hasChat
        ? queryClient.getQueryData<ChatNotificationSettingsPreferences>(
            RQKEY_CHAT,
          )
        : undefined

      /*
       * Apply the optimistic update synchronously before cancelling queries so
       * rapid consecutive toggles read the latest optimistic preference.
       */
      optimisticUpdateNotificationSettings(queryClient, update)
      if (hasApp && currentAccount?.did) {
        const nextApp =
          queryClient.getQueryData<AppNotificationSettingsPreferences>(
            RQKEY_APP,
          ) ?? appUpdate
        void syncDisabledPushReasonsNative(currentAccount.did, nextApp)
      }

      await Promise.all([
        hasApp ? queryClient.cancelQueries({queryKey: RQKEY_APP}) : undefined,
        hasChat ? queryClient.cancelQueries({queryKey: RQKEY_CHAT}) : undefined,
      ])

      return {previousApp, previousChat}
    },
    onSuccess: data => {
      if (
        data.appPreferences &&
        countPendingSideMutations(queryClient, 'app') <= 1
      ) {
        queryClient.setQueryData(RQKEY_APP, data.appPreferences)
        if (currentAccount?.did) {
          void syncDisabledPushReasonsNative(
            currentAccount.did,
            data.appPreferences,
          )
          void syncPreferencesToPushGateway({
            client: pdsClient,
            did: currentAccount.did,
            preferences: data.appPreferences,
            force: true,
          })
        }
      }
      if (
        data.chatPreferences &&
        countPendingSideMutations(queryClient, 'chat') <= 1
      ) {
        queryClient.setQueryData(RQKEY_CHAT, data.chatPreferences)
      }
    },
    onError: (e, update, context) => {
      logger.error('Could not update notification settings', {message: e})
      const {appUpdate, chatUpdate} = splitNotificationSettingsUpdate(update)
      if (
        hasUpdates(appUpdate) &&
        countPendingSideMutations(queryClient, 'app') <= 1
      ) {
        if (context?.previousApp !== undefined) {
          queryClient.setQueryData(RQKEY_APP, context.previousApp)
          if (currentAccount?.did) {
            void syncDisabledPushReasonsNative(
              currentAccount.did,
              context.previousApp,
            )
          }
        }
        void queryClient.invalidateQueries({queryKey: RQKEY_APP})
      }
      if (
        hasUpdates(chatUpdate) &&
        countPendingSideMutations(queryClient, 'chat') <= 1
      ) {
        if (context?.previousChat !== undefined) {
          queryClient.setQueryData(RQKEY_CHAT, context.previousChat)
        }
        void queryClient.invalidateQueries({queryKey: RQKEY_CHAT})
      }
      Toast.show(t`Could not update notification settings`, {
        type: 'error',
      })
    },
  })
}

function optimisticUpdateNotificationSettings(
  queryClient: QueryClient,
  update: NotificationSettingsUpdate,
) {
  const {appUpdate, chatUpdate} = splitNotificationSettingsUpdate(update)

  if (hasUpdates(appUpdate)) {
    queryClient.setQueryData(
      RQKEY_APP,
      (old?: AppNotificationSettingsPreferences) => {
        if (!old) return old
        return {...old, ...appUpdate}
      },
    )
  }

  if (hasUpdates(chatUpdate)) {
    queryClient.setQueryData(
      RQKEY_CHAT,
      (old?: ChatNotificationSettingsPreferences) => {
        if (!old) return old
        return {...old, ...chatUpdate}
      },
    )
  }
}

function appPreferencesWithoutChat(
  preferences: app.bsky.notification.defs.Preferences,
): Omit<app.bsky.notification.defs.Preferences, 'chat'> {
  const {chat: _ignoredChat, ...appPreferences} = preferences
  return appPreferences
}

function chatPreferencesForSettings(
  preferences: chat.bsky.notification.defs.Preferences,
): Pick<chat.bsky.notification.defs.Preferences, 'chat' | 'chatRequest'> {
  return {
    chat: preferences.chat,
    chatRequest: preferences.chatRequest,
  }
}

function splitNotificationSettingsUpdate(update: NotificationSettingsUpdate): {
  appUpdate: AppNotificationSettingsUpdate
  chatUpdate: ChatNotificationSettingsUpdate
} {
  const {chat, chatRequest, $type: _type, ...appUpdate} = update

  return {
    appUpdate: appUpdate,
    chatUpdate: {
      ...(chat !== undefined ? {chat} : {}),
      ...(chatRequest !== undefined ? {chatRequest} : {}),
    },
  }
}

function hasUpdates(update: object) {
  return Object.keys(update).length > 0
}

function countPendingSideMutations(
  queryClient: QueryClient,
  side: 'app' | 'chat',
) {
  return queryClient
    .getMutationCache()
    .findAll({mutationKey: MUTATION_KEY, status: 'pending'})
    .filter(mutation => {
      const variables = mutation.state.variables as
        NotificationSettingsUpdate | undefined
      if (!variables) return false
      const {appUpdate, chatUpdate} = splitNotificationSettingsUpdate(variables)
      return side === 'app' ? hasUpdates(appUpdate) : hasUpdates(chatUpdate)
    }).length
}
