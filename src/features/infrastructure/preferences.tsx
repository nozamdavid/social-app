import {createContext, useContext, useSyncExternalStore} from 'react'

import {IS_BLUESKY_PLUS} from '#/env'
import {device} from '#/storage'
import {
  DEFAULT_INFRASTRUCTURE,
  type InfrastructureSettings,
  readInfrastructureSettings,
} from './config'

const Context = createContext(DEFAULT_INFRASTRUCTURE)
let previousRaw: string | undefined
let snapshot = DEFAULT_INFRASTRUCTURE

/** Read the current settings at request dispatch, including existing clients. */
export function getInfrastructureSettings(): InfrastructureSettings {
  if (!IS_BLUESKY_PLUS) return DEFAULT_INFRASTRUCTURE
  const raw = device.getRaw(['infrastructure'])
  if (raw !== previousRaw) {
    previousRaw = raw
    try {
      const parsed: unknown = raw ? JSON.parse(raw) : undefined
      snapshot = readInfrastructureSettings(
        parsed && typeof parsed === 'object' && 'data' in parsed
          ? parsed.data
          : undefined,
      )
    } catch {
      snapshot = DEFAULT_INFRASTRUCTURE
    }
  }
  return snapshot
}

export function saveInfrastructureSettings(settings: InfrastructureSettings) {
  device.set(['infrastructure'], settings)
}

/** Save one control without overwriting other controls or committing drafts. */
export function updateInfrastructureSettings(
  patch: Partial<InfrastructureSettings>,
) {
  saveInfrastructureSettings({...getInfrastructureSettings(), ...patch})
}

/** Subscribe without passing raw storage through useStorage's JSON parser. */
function subscribe(listener: () => void) {
  const subscription = device.addOnValueChangedListener(
    ['infrastructure'],
    listener,
  )
  return () => subscription.remove()
}

export function InfrastructureProvider({
  children,
}: {
  children: React.ReactNode
}) {
  const settings = useSyncExternalStore(
    subscribe,
    getInfrastructureSettings,
    getInfrastructureSettings,
  )
  return <Context.Provider value={settings}>{children}</Context.Provider>
}

export function useInfrastructureSettings() {
  return useContext(Context)
}
