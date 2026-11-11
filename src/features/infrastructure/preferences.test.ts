import {createElement} from 'react'
import {act, renderHook} from '@testing-library/react-native'

import {device} from '#/storage'
import {DEFAULT_INFRASTRUCTURE} from './config'
import {
  getInfrastructureSettings,
  InfrastructureProvider,
  saveInfrastructureSettings,
  useInfrastructureSettings,
} from './preferences'

jest.mock('#/env', () => ({IS_BLUESKY_PLUS: true}))
let mockRaw: string | undefined
const mockListeners = new Set<() => void>()
jest.mock('#/storage', () => ({
  device: {
    getRaw: jest.fn(() => mockRaw),
    set: jest.fn((_keys: string[], value: unknown) => {
      mockRaw = JSON.stringify({data: value})
      mockListeners.forEach(listener => listener())
    }),
    addOnValueChangedListener: jest.fn(
      (_keys: string[], listener: () => void) => {
        mockListeners.add(listener)
        return {remove: () => mockListeners.delete(listener)}
      },
    ),
  },
}))

beforeEach(() => {
  mockRaw = undefined
  mockListeners.clear()
  getInfrastructureSettings()
  jest.clearAllMocks()
})

it('renders the provider with corrupt JSON, recovers on updates, and unsubscribes', () => {
  mockRaw = '{corrupt startup data'
  const {result, unmount} = renderHook(useInfrastructureSettings, {
    wrapper: ({children}) =>
      createElement(InfrastructureProvider, null, children),
  })
  expect(result.current).toEqual(DEFAULT_INFRASTRUCTURE)
  expect(mockListeners.size).toBe(1)
  act(() =>
    saveInfrastructureSettings({
      ...DEFAULT_INFRASTRUCTURE,
      imageCdnHostname: 'cdn.example.com',
    }),
  )
  expect(result.current.imageCdnHostname).toBe('cdn.example.com')
  act(() => {
    mockRaw = '{corrupt updated data'
    mockListeners.forEach(listener => listener())
  })
  expect(result.current).toEqual(DEFAULT_INFRASTRUCTURE)
  unmount()
  expect(mockListeners.size).toBe(0)
})

it('persists settings for all accounts and reads updates without rebuilding a client', () => {
  expect(getInfrastructureSettings()).toEqual(DEFAULT_INFRASTRUCTURE)
  const settings = {
    ...DEFAULT_INFRASTRUCTURE,
    useLinkRedirects: false,
    appviewHostname: 'api.eurosky.network',
    imageCdnHostname: 'cdn.example.com',
  }
  saveInfrastructureSettings(settings)
  expect(device.set).toHaveBeenCalledWith(['infrastructure'], settings)
  expect(getInfrastructureSettings()).toEqual(settings)
  saveInfrastructureSettings(DEFAULT_INFRASTRUCTURE)
  expect(getInfrastructureSettings()).toEqual(DEFAULT_INFRASTRUCTURE)
})

it('falls back for corrupt JSON and malformed stored hostnames', () => {
  mockRaw = '{broken'
  expect(getInfrastructureSettings()).toEqual(DEFAULT_INFRASTRUCTURE)
  mockRaw = JSON.stringify({
    data: {
      appviewHostname: 'https://user:pass@host.example/path',
      useLinkRedirects: false,
    },
  })
  expect(getInfrastructureSettings()).toEqual({
    ...DEFAULT_INFRASTRUCTURE,
    useLinkRedirects: false,
  })
})
