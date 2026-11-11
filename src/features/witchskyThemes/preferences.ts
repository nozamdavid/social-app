import {useCallback, useSyncExternalStore} from 'react'

import {useSession} from '#/state/session'
import {IS_BLUESKY_PLUS} from '#/env'
import {
  readThemeSelection,
  type ThemeOption,
} from '#/features/witchskyThemes/schema'
import {account} from '#/storage'

/** Account-scoped cached colors apply immediately, including offline startup. */
export function useWitchskyThemeSelection() {
  const {currentAccount, hasSession} = useSession()
  const did = IS_BLUESKY_PLUS && hasSession ? currentAccount?.did : undefined
  const subscribe = useCallback(
    (callback: () => void) => {
      if (!did) return () => {}
      const listener = account.addOnValueChangedListener(
        [did, 'witchskyTheme'],
        callback,
      )
      return () => listener.remove()
    },
    [did],
  )
  const raw = useSyncExternalStore(
    subscribe,
    () => (did ? account.getRaw([did, 'witchskyTheme']) : undefined),
    () => undefined,
  )
  const selection = did ? readThemeSelection(raw, did) : null
  function selectTheme(theme: ThemeOption | null) {
    if (did) account.set([did, 'witchskyTheme'], theme)
  }
  return [selection, selectTheme] as const
}
