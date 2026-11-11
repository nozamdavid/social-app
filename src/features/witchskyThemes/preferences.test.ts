import {act, renderHook} from '@testing-library/react-native'

import {useWitchskyThemeSelection} from '#/features/witchskyThemes/preferences'
import {
  parseThemeRecord,
  THEME_COLLECTION,
} from '#/features/witchskyThemes/schema'
import {useThemeApplication} from '#/features/witchskyThemes/useThemeApplication'

const mockValues = new Map<string, string>()
const mockListeners = new Map<string, Set<() => void>>()
let mockDid: string | undefined = 'did:plc:first'
let mockHasSession = true
let mockWriteCount = 0
let mockFailWrite = false

jest.mock('#/env', () => ({IS_BLUESKY_PLUS: true}))
jest.mock('#/state/session', () => ({
  useSession: () => ({
    currentAccount: mockDid ? {did: mockDid} : undefined,
    hasSession: mockHasSession,
  }),
}))
jest.mock('#/storage', () => ({
  account: {
    getRaw: (keys: string[]) => mockValues.get(keys.join(':')),
    set: (keys: string[], value: unknown) => {
      if (mockFailWrite) throw new Error('Storage unavailable')
      mockWriteCount++
      const key = keys.join(':')
      mockValues.set(key, JSON.stringify({data: value}))
      mockListeners.get(key)?.forEach(callback => callback())
    },
    addOnValueChangedListener: (keys: string[], callback: () => void) => {
      const key = keys.join(':')
      const listeners = mockListeners.get(key) ?? new Set()
      listeners.add(callback)
      mockListeners.set(key, listeners)
      return {remove: () => listeners.delete(callback)}
    },
  },
}))

const mockFrames = new Map<number, FrameRequestCallback>()
let mockFrameId = 0

function paintFrame() {
  const callbacks = [...mockFrames.values()]
  mockFrames.clear()
  act(() => callbacks.forEach(callback => callback(0)))
}

afterEach(() => {
  jest.restoreAllMocks()
})

const colors = {
  canvas: '#112233',
  surface: '#223344',
  surfaceRaised: '#334455',
  text: '#ffffff',
  textMuted: '#aabbcc',
  border: '#445566',
  accent: '#00ffff',
  accentSoft: '#123456',
  onAccent: '#000000',
  positive: '#00ff00',
  warning: '#ffff00',
  critical: '#ff0000',
  favorite: '#ff00ff',
}
const [theme] = parseThemeRecord(
  `at://did:plc:first/${THEME_COLLECTION}/example`,
  {
    $type: THEME_COLLECTION,
    name: 'Example',
    mode: 'dark',
    base: {name: 'Base', colors},
    createdAt: '2026-11-09T00:00:00Z',
  },
)

beforeEach(() => {
  mockValues.clear()
  mockListeners.clear()
  mockDid = 'did:plc:first'
  mockHasSession = true
  mockWriteCount = 0
  mockFailWrite = false
  mockFrames.clear()
  jest.spyOn(global, 'requestAnimationFrame').mockImplementation(callback => {
    const id = ++mockFrameId
    mockFrames.set(id, callback)
    return id
  })
  jest.spyOn(global, 'cancelAnimationFrame').mockImplementation(id => {
    if (typeof id === 'number') mockFrames.delete(id)
  })
})

it('persists selection, restores it after remount, and clears it with Default', () => {
  const first = renderHook(() => useWitchskyThemeSelection())
  expect(first.result.current[0]).toBeNull()
  act(() => first.result.current[1](theme))
  expect(first.result.current[0]).toEqual(theme)
  first.unmount()
  const second = renderHook(() => useWitchskyThemeSelection())
  expect(second.result.current[0]).toEqual(theme)
  act(() => second.result.current[1](null))
  expect(second.result.current[0]).toBeNull()
})

it('switches accounts without exposing the previous account’s cached colors', () => {
  const hook = renderHook(() => useWitchskyThemeSelection())
  act(() => hook.result.current[1](theme))
  mockDid = 'did:plc:second'
  hook.rerender({})
  expect(hook.result.current[0]).toBeNull()
  mockDid = 'did:plc:first'
  hook.rerender({})
  expect(hook.result.current[0]).toEqual(theme)
  mockHasSession = false
  hook.rerender({})
  expect(hook.result.current[0]).toBeNull()
})

it('acknowledges the radio choice and modal before changing the app-wide theme', () => {
  const hook = renderHook(() => ({
    application: useThemeApplication(),
    applied: useWitchskyThemeSelection()[0],
  }))
  act(() => hook.result.current.application.requestTheme(theme))
  expect(hook.result.current.application.radioId).toBe(theme.id)
  expect(hook.result.current.application.applying).toBe(true)
  expect(hook.result.current.applied).toBeNull()
})

it('waits for modal presentation and a painted frame, then hides it after the themed commit', () => {
  const hook = renderHook(() => ({
    application: useThemeApplication(),
    applied: useWitchskyThemeSelection()[0],
  }))
  act(() => hook.result.current.application.requestTheme(theme))
  paintFrame()
  expect(hook.result.current.applied).toBeNull()
  act(() => {
    hook.result.current.application.onModalShow()
    hook.result.current.application.onModalShow()
    hook.result.current.application.requestTheme(null)
  })
  paintFrame()
  expect(hook.result.current.applied).toBeNull()
  paintFrame()
  expect(hook.result.current.applied).toEqual(theme)
  expect(mockWriteCount).toBe(1)
  expect(hook.result.current.application.applying).toBe(true)
  paintFrame()
  expect(hook.result.current.application.applying).toBe(true)
  paintFrame()
  expect(hook.result.current.application.applying).toBe(false)
})

it('uses the same feedback sequence when restoring Default', () => {
  mockValues.set('did:plc:first:witchskyTheme', JSON.stringify({data: theme}))
  const hook = renderHook(() => ({
    application: useThemeApplication(),
    applied: useWitchskyThemeSelection()[0],
  }))
  act(() => hook.result.current.application.requestTheme(null))
  expect(hook.result.current.application.radioId).toBe('default')
  expect(hook.result.current.applied).toEqual(theme)
  act(() => hook.result.current.application.onModalShow())
  paintFrame()
  paintFrame()
  expect(hook.result.current.applied).toBeNull()
  paintFrame()
  paintFrame()
  expect(hook.result.current.application.applying).toBe(false)
})

it('cancels a scheduled application when unmounted or the account changes', () => {
  const first = renderHook(() => useThemeApplication())
  act(() => first.result.current.requestTheme(theme))
  act(() => first.result.current.onModalShow())
  first.unmount()
  paintFrame()
  paintFrame()
  expect(mockWriteCount).toBe(0)

  const second = renderHook(() => useThemeApplication())
  act(() => second.result.current.requestTheme(theme))
  act(() => second.result.current.onModalShow())
  mockDid = 'did:plc:second'
  second.rerender({})
  paintFrame()
  paintFrame()
  expect(mockWriteCount).toBe(0)
  expect(second.result.current.applying).toBe(false)
  expect(second.result.current.radioId).toBe('default')
})

it('dismisses progress and restores the applied radio selection if saving fails', () => {
  const hook = renderHook(() => useThemeApplication())
  mockFailWrite = true
  act(() => hook.result.current.requestTheme(theme))
  act(() => hook.result.current.onModalShow())
  paintFrame()
  paintFrame()
  expect(hook.result.current.applying).toBe(false)
  expect(hook.result.current.radioId).toBe('default')
  expect(hook.result.current.failed).toBe(true)
  mockFailWrite = false
  act(() => hook.result.current.requestTheme(theme))
  expect(hook.result.current.failed).toBe(false)
  act(() => hook.result.current.onModalShow())
  paintFrame()
  paintFrame()
  expect(mockWriteCount).toBe(1)
})
