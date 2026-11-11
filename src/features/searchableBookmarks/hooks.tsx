import {useCallback, useEffect, useSyncExternalStore} from 'react'
import {useQueryClient} from '@tanstack/react-query'

import {useAppviewClient, useSession} from '#/state/session'
import {IS_BLUESKY_PLUS} from '#/env'
import {
  getInfrastructureSettings,
  useInfrastructureSettings,
} from '#/features/infrastructure/preferences'
import {app} from '#/lexicons'
import {device} from '#/storage'
import * as store from './store'

export function getEnabled() {
  try {
    return IS_BLUESKY_PLUS && device.get(['searchableBookmarks']) === true
  } catch {
    return false
  }
}
function subscribeEnabled(listener: () => void) {
  const sub = device.addOnValueChangedListener(
    ['searchableBookmarks'],
    listener,
  )
  return () => sub.remove()
}

export function useBookmarkScope() {
  const enabled = useSyncExternalStore(subscribeEnabled, getEnabled, getEnabled)
  const {currentAccount} = useSession()
  const {appviewHostname} = useInfrastructureSettings()
  const client = useAppviewClient()
  const key = JSON.stringify([
    currentAccount?.did,
    appviewHostname || 'api.bsky.app',
  ])
  const sync = useCallback(
    (options?: {full?: boolean}) => {
      if (!currentAccount || !getEnabled()) return Promise.resolve()
      return store.sync(
        key,
        cursor =>
          client.call(app.bsky.bookmark.getBookmarks, {cursor, limit: 100}),
        () =>
          getEnabled() &&
          getInfrastructureSettings().appviewHostname === appviewHostname,
        options,
      )
    },
    [currentAccount, client, key, appviewHostname],
  )
  return {
    enabled,
    key,
    sync,
    setEnabled(value: boolean) {
      device.set(['searchableBookmarks'], value)
    },
    clear: () => store.clear(key),
  }
}

export function useLocalBookmarks() {
  const scope = useBookmarkScope()
  const status = useSyncExternalStore(store.subscribe, () =>
    store.getStatus(scope.key),
  )
  return {...scope, status}
}

/** Keep imports alive across navigation and stop them on account/source changes. */
export function LocalBookmarksSync() {
  const {enabled, key, status, sync} = useLocalBookmarks()
  const qc = useQueryClient()
  useEffect(() => {
    if (enabled) void sync()
    return () => store.stop(key)
  }, [enabled, key, sync])
  useEffect(() => {
    void qc.invalidateQueries({queryKey: ['bookmarks', 'local', key]})
  }, [qc, key, status.revision])
  return null
}
