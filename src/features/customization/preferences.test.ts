import {act, renderHook} from '@testing-library/react-native'

import {
  getAutoLikeReposts,
  setAutoLikeReposts,
  useAutoLikeReposts,
} from '#/features/customization/preferences'
import {device} from '#/storage'

let mockRaw: string | undefined
const mockListeners = new Set<() => void>()
jest.mock('#/env', () => ({IS_BLUESKY_PLUS: true}))
jest.mock('#/storage', () => ({
  device: {
    getRaw: () => mockRaw,
    set: jest.fn((_keys: string[], value: unknown) => {
      mockRaw = JSON.stringify({data: value})
      mockListeners.forEach(listener => listener())
    }),
    addOnValueChangedListener: (_keys: string[], listener: () => void) => {
      mockListeners.add(listener)
      return {remove: () => mockListeners.delete(listener)}
    },
  },
}))

beforeEach(() => {
  mockRaw = undefined
  mockListeners.clear()
  jest.clearAllMocks()
})

it.each([
  undefined,
  '{broken',
  'null',
  '{"data":"true"}',
  '{"data":1}',
  '{"data":false}',
])('defaults off for missing or malformed data (%s)', raw => {
  mockRaw = raw
  expect(getAutoLikeReposts()).toBe(false)
})

it('saves immediately, updates subscribers, survives remount, and cleans up', () => {
  const first = renderHook(useAutoLikeReposts)
  expect(first.result.current).toBe(false)
  act(() => setAutoLikeReposts(true))
  expect(first.result.current).toBe(true)
  expect(device.set).toHaveBeenCalledWith(['autoLikeReposts'], true)
  first.unmount()
  expect(mockListeners.size).toBe(0)
  const second = renderHook(useAutoLikeReposts)
  expect(second.result.current).toBe(true)
  act(() => setAutoLikeReposts(false))
  expect(second.result.current).toBe(false)
})
