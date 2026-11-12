import {AppState, type AppStateStatus} from 'react-native'
import {act, renderHook} from '@testing-library/react-native'

import * as native from '#/features/altTextGeneration/native'
import {type DescriptionStatus} from '#/features/altTextGeneration/types'
import {useDescriptionSupported} from '#/features/altTextGeneration/useDescriptionSupported'

jest.mock('#/features/altTextGeneration/native', () => ({
  isAvailable: true,
  getDescriptionStatus: jest.fn(),
}))

let onAppStateChange: (state: AppStateStatus) => void
const removeListener = jest.fn()

beforeEach(() => {
  jest.clearAllMocks()
  jest.mocked(native.getDescriptionStatus).mockResolvedValue('unavailable')
  jest.spyOn(AppState, 'addEventListener').mockImplementation((_, listener) => {
    onAppStateChange = listener
    return {remove: removeListener}
  })
})

afterEach(() => jest.restoreAllMocks())

it('keeps Describe hidden until support is confirmed, without starting a download', async () => {
  let resolve!: (status: DescriptionStatus) => void
  const pending = new Promise<DescriptionStatus>(yes => {
    resolve = yes
  })
  jest.mocked(native.getDescriptionStatus).mockReturnValue(pending)
  const {result} = renderHook(() => useDescriptionSupported())
  expect(result.current).toBe(false)
  await act(async () => {
    resolve('available')
    await pending
  })
  expect(result.current).toBe(true)
})

it.each(['downloadable', 'downloading'] as const)(
  'shows Describe on capable devices even when the model is %s',
  async status => {
    jest.mocked(native.getDescriptionStatus).mockResolvedValue(status)
    const {result} = renderHook(() => useDescriptionSupported())
    await act(async () => {})
    expect(result.current).toBe(true)
  },
)

it('hides Describe on unsupported devices', async () => {
  const {result} = renderHook(() => useDescriptionSupported())
  await act(async () => {})
  expect(result.current).toBe(false)
})

it('rechecks on foreground after a failed check and removes the listener on close', async () => {
  jest
    .mocked(native.getDescriptionStatus)
    .mockRejectedValueOnce({code: 'ERR_TIMEOUT'})
    .mockResolvedValueOnce('available')
  const {result, unmount} = renderHook(() => useDescriptionSupported())
  await act(async () => {})
  expect(result.current).toBe(false)
  act(() => onAppStateChange('background'))
  expect(native.getDescriptionStatus).toHaveBeenCalledTimes(1)
  await act(async () => {
    onAppStateChange('active')
    await Promise.resolve()
  })
  expect(result.current).toBe(true)
  unmount()
  expect(removeListener).toHaveBeenCalledTimes(1)
})

it('ignores an older capability result when a newer check has finished', async () => {
  let resolveOld!: (status: DescriptionStatus) => void
  const old = new Promise<DescriptionStatus>(yes => {
    resolveOld = yes
  })
  jest
    .mocked(native.getDescriptionStatus)
    .mockReturnValueOnce(old)
    .mockResolvedValueOnce('unavailable')
  const {result} = renderHook(() => useDescriptionSupported())
  await act(async () => {
    onAppStateChange('active')
    await Promise.resolve()
  })
  await act(async () => {
    resolveOld('available')
    await old
  })
  expect(result.current).toBe(false)
})
