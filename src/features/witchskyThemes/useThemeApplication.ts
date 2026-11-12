import {useEffect, useRef, useState} from 'react'

import {useSession} from '#/state/session'
import {useWitchskyThemeSelection} from '#/features/witchskyThemes/preferences'
import {type ThemeOption} from '#/features/witchskyThemes/schema'

/** Give the picker and native modal a painted frame before updating the app. */
export function useThemeApplication() {
  const [selected, selectTheme] = useWitchskyThemeSelection()
  const {currentAccount, hasSession} = useSession()
  const scope = hasSession ? currentAccount?.did : undefined
  const [pending, setPending] = useState<{
    theme: ThemeOption | null
    scope: string
  }>()
  const [committed, setCommitted] = useState(false)
  const [failed, setFailed] = useState(false)
  const busy = useRef(false)
  const started = useRef(false)
  const frame = useRef<number | undefined>(undefined)

  function cancelFrame() {
    if (frame.current !== undefined) cancelAnimationFrame(frame.current)
    frame.current = undefined
  }

  function afterPaint(callback: () => void) {
    frame.current = requestAnimationFrame(() => {
      frame.current = requestAnimationFrame(() => {
        frame.current = undefined
        callback()
      })
    })
  }

  useEffect(() => {
    setPending(undefined)
    setCommitted(false)
    setFailed(false)
    busy.current = false
    started.current = false
    return cancelFrame
  }, [scope])

  useEffect(() => {
    if (!committed) return
    /* Keep the modal until React has committed and painted the themed tree. */
    afterPaint(() => {
      busy.current = false
      started.current = false
      setPending(undefined)
      setCommitted(false)
    })
    return cancelFrame
  }, [committed])

  function requestTheme(theme: ThemeOption | null) {
    if (busy.current || !scope) return
    busy.current = true
    setFailed(false)
    setPending({theme, scope})
  }

  function onModalShow() {
    if (!pending || pending.scope !== scope || started.current) return
    started.current = true
    /* onShow confirms the native window is visible, rather than just scheduled. */
    afterPaint(() => {
      try {
        selectTheme(pending.theme)
        setCommitted(true)
      } catch {
        busy.current = false
        started.current = false
        setPending(undefined)
        setFailed(true)
      }
    })
  }

  const active = pending?.scope === scope ? pending : undefined
  return {
    selected,
    radioId: (active ? active.theme?.id : selected?.id) ?? 'default',
    applying: !!active,
    failed,
    requestTheme,
    onModalShow,
  }
}
