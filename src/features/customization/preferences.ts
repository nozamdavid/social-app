import {useSyncExternalStore} from 'react'

import {IS_BLUESKY_PLUS} from '#/env'
import {device} from '#/storage'

/** Opt-in behavior shared by accounts on this device; malformed values are off. */
export function getAutoLikeReposts(): boolean {
  if (!IS_BLUESKY_PLUS) return false
  try {
    const raw = device.getRaw(['autoLikeReposts'])
    const value: unknown = raw ? JSON.parse(raw) : undefined
    return !!(
      value &&
      typeof value === 'object' &&
      'data' in value &&
      value.data === true
    )
  } catch {
    return false
  }
}

function subscribe(listener: () => void) {
  const subscription = device.addOnValueChangedListener(
    ['autoLikeReposts'],
    listener,
  )
  return () => subscription.remove()
}

export function useAutoLikeReposts() {
  return useSyncExternalStore(subscribe, getAutoLikeReposts, getAutoLikeReposts)
}

export function setAutoLikeReposts(enabled: boolean) {
  device.set(['autoLikeReposts'], enabled)
}

export function useSetAutoLikeReposts() {
  return setAutoLikeReposts
}
