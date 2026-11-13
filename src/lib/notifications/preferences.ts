import {useEffect} from 'react'
import {type Client} from '@atproto/lex'
import BackgroundNotificationHandler from '@bsky.app/expo-background-notification-handler'

import {NOTIF_SERVICE, PUBLIC_APPVIEW_DID} from '#/lib/constants'
import {
  getPushRegistration,
  PUSH_ENABLED,
} from '#/lib/notifications/registration'
import {logger as notyLogger} from '#/lib/notifications/util'
import {isNetworkError} from '#/lib/strings/errors'
import {IS_NATIVE, PUSH_GATEWAY_DID} from '#/env'
import {app} from '#/lexicons'

export type AppNotificationPreferences = Omit<
  app.bsky.notification.defs.Preferences,
  'chat'
>

const PREF_KEY_TO_PUSH_REASON: Record<
  Exclude<keyof AppNotificationPreferences, '$type'>,
  string
> = {
  follow: 'follow',
  like: 'like',
  likeViaRepost: 'like-via-repost',
  mention: 'mention',
  quote: 'quote',
  reply: 'reply',
  repost: 'repost',
  repostViaRepost: 'repost-via-repost',
  starterpackJoined: 'starterpack-joined',
  subscribedPost: 'subscribed-post',
  unverified: 'unverified',
  verified: 'verified',
}

const PUSH_REASON_TO_PREF_KEY = Object.fromEntries(
  Object.entries(PREF_KEY_TO_PUSH_REASON).map(([prefKey, reason]) => [
    reason,
    prefKey as Exclude<keyof AppNotificationPreferences, '$type'>,
  ]),
) as Record<
  string,
  Exclude<keyof AppNotificationPreferences, '$type'> | undefined
>

const lastSyncedGatewaySignatures = new Map<string, string>()

export function getDisabledPushReasons(
  preferences: Partial<AppNotificationPreferences>,
): string[] {
  const disabled: string[] = []
  for (const [prefKey, reason] of Object.entries(PREF_KEY_TO_PUSH_REASON) as [
    Exclude<keyof AppNotificationPreferences, '$type'>,
    string,
  ][]) {
    if (preferences[prefKey]?.push === false) {
      disabled.push(reason)
    }
  }
  return disabled.sort()
}

export function isNotificationReasonPushDisabled(
  preferences: Partial<AppNotificationPreferences> | undefined,
  reason: string | undefined,
): boolean {
  if (!preferences || !reason) return false
  const prefKey = PUSH_REASON_TO_PREF_KEY[reason]
  if (!prefKey) return false
  return preferences[prefKey]?.push === false
}

export async function syncDisabledPushReasonsNative(
  did: string | undefined,
  preferences: Partial<AppNotificationPreferences> | undefined,
) {
  if (!IS_NATIVE || !did || !preferences) return
  try {
    await BackgroundNotificationHandler.setDisabledPushReasonsAsync(
      did,
      getDisabledPushReasons(preferences),
    )
  } catch (error) {
    notyLogger.debug('syncDisabledPushReasonsNative: failed', {
      safeMessage: error,
    })
  }
}

function buildGatewayPreferenceInput(
  preferences: Partial<AppNotificationPreferences>,
): app.bsky.notification.putPreferencesV2.$InputBody {
  const payload: app.bsky.notification.putPreferencesV2.$InputBody = {}
  for (const key of Object.keys(PREF_KEY_TO_PUSH_REASON) as Exclude<
    keyof AppNotificationPreferences,
    '$type'
  >[]) {
    const value = preferences[key]
    if (value !== undefined) {
      Object.assign(payload, {[key]: value})
    }
  }
  return payload
}

function getGatewaySyncSignature(
  did: string,
  gatewayDid: string,
  preferences: Partial<AppNotificationPreferences>,
) {
  const states = (
    Object.keys(PREF_KEY_TO_PUSH_REASON) as Exclude<
      keyof AppNotificationPreferences,
      '$type'
    >[]
  ).map(key => `${key}:${preferences[key]?.push !== false ? '1' : '0'}`)
  return `${did}|${gatewayDid}|${states.join(',')}`
}

export function clearPushGatewaySyncCache() {
  lastSyncedGatewaySignatures.clear()
}

export async function syncPreferencesToPushGateway({
  client,
  did,
  preferences,
  force = false,
}: {
  client: Client | null | undefined
  did: string | undefined
  preferences: Partial<AppNotificationPreferences> | undefined
  force?: boolean
}) {
  if (!PUSH_ENABLED || !PUSH_GATEWAY_DID || !client || !did || !preferences) {
    return
  }

  const {serviceDid} = getPushRegistration()
  if (serviceDid === PUBLIC_APPVIEW_DID) {
    return
  }

  const input = buildGatewayPreferenceInput(preferences)
  if (Object.keys(input).length === 0) {
    return
  }

  const signature = getGatewaySyncSignature(did, serviceDid, preferences)
  if (!force && lastSyncedGatewaySignatures.get(did) === signature) {
    return
  }

  try {
    await client.call(app.bsky.notification.putPreferencesV2, input, {
      service: NOTIF_SERVICE,
    })
    lastSyncedGatewaySignatures.set(did, signature)
  } catch (error) {
    lastSyncedGatewaySignatures.delete(did)
    if (!isNetworkError(error)) {
      notyLogger.debug('syncPreferencesToPushGateway: failed', {
        safeMessage: error,
      })
    }
  }
}

export function useSyncEffectForNotificationPreferences({
  did,
  preferences,
  pdsClient,
}: {
  did: string | undefined
  preferences: AppNotificationPreferences | undefined
  pdsClient: Client | null
}) {
  useEffect(() => {
    if (!did || !preferences) return
    void syncDisabledPushReasonsNative(did, preferences)
    void syncPreferencesToPushGateway({
      client: pdsClient,
      did,
      preferences,
    })
  }, [did, preferences, pdsClient])
}
