import {act, renderHook} from '@testing-library/react-native'

import * as native from '#/features/altTextGeneration/native'
import {type GenerationProgress} from '#/features/altTextGeneration/types'
import {useAltTextGeneration} from '#/features/altTextGeneration/useAltTextGeneration'

let mockProgress: ((event: GenerationProgress) => void) | undefined
const mockRemoveListener = jest.fn()

jest.mock('#/features/altTextGeneration/native', () => ({
  getDescriptionStatus: jest.fn(),
  generate: jest.fn(),
  cancel: jest.fn(),
  onProgress: (listener: (event: GenerationProgress) => void) => {
    mockProgress = listener
    return {remove: mockRemoveListener}
  },
}))

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (error: unknown) => void
  const promise = new Promise<T>((yes, no) => {
    resolve = yes
    reject = no
  })
  return {promise, resolve, reject}
}

beforeEach(() => {
  jest.clearAllMocks()
  jest.mocked(native.getDescriptionStatus).mockResolvedValue('available')
  jest.mocked(native.generate).mockResolvedValue('Suggested text')
})

it('returns a reviewable draft from the provided local crop without checking GenAI', async () => {
  const {result} = renderHook(() => useAltTextGeneration('file:///crop.jpg'))
  await act(() => result.current.start('ocr'))
  expect(native.getDescriptionStatus).not.toHaveBeenCalled()
  expect(native.generate).toHaveBeenCalledWith(
    expect.any(String),
    'ocr',
    'file:///crop.jpg',
    false,
  )
  expect(result.current.suggestion?.text).toBe('Suggested text')
  expect(result.current.busy).toBe(false)
})

it.each(['downloadable', 'downloading'] as const)(
  'requires an explicit download action when the model is %s',
  async status => {
    jest.mocked(native.getDescriptionStatus).mockResolvedValue(status)
    const {result} = renderHook(() => useAltTextGeneration('file:///image.jpg'))
    await act(() => result.current.start('describe'))
    expect(native.generate).not.toHaveBeenCalled()
    expect(result.current.downloadRequired).toBe(true)
    expect(result.current.busy).toBe(false)

    await act(() => result.current.start('describe', true))
    expect(native.generate).toHaveBeenCalledWith(
      expect.any(String),
      'describe',
      'file:///image.jpg',
      true,
    )
    expect(result.current.suggestion?.text).toBe('Suggested text')
  },
)

it('leaves OCR usable when descriptions are unavailable', async () => {
  jest.mocked(native.getDescriptionStatus).mockResolvedValue('unavailable')
  const {result} = renderHook(() => useAltTextGeneration('file:///image.jpg'))
  await act(() => result.current.start('describe'))
  expect(result.current.error).toBe('ERR_UNAVAILABLE')
  expect(native.generate).not.toHaveBeenCalled()
  await act(() => result.current.start('ocr'))
  expect(result.current.error).toBeNull()
  expect(result.current.suggestion?.text).toBe('Suggested text')
})

it('ignores a cancelled result and its progress while a newer request runs', async () => {
  const old = deferred<string>()
  const newer = deferred<string>()
  jest
    .mocked(native.generate)
    .mockReturnValueOnce(old.promise)
    .mockReturnValueOnce(newer.promise)
  const {result} = renderHook(() => useAltTextGeneration('file:///image.jpg'))
  let first!: Promise<void>
  act(() => {
    first = result.current.start('ocr')
  })
  const oldId = jest.mocked(native.generate).mock.calls[0][0]
  act(() => result.current.cancel())
  let second!: Promise<void>
  act(() => {
    second = result.current.start('ocr')
  })
  await act(async () => {
    mockProgress?.({
      requestId: oldId,
      phase: 'downloading',
      downloadedBytes: 5,
      totalBytes: 10,
    })
    old.resolve('Stale text')
    await first
  })
  expect(native.cancel).toHaveBeenCalledWith(oldId)
  expect(result.current.phase).toBe('recognizing')
  expect(result.current.suggestion).toBeNull()
  await act(async () => {
    newer.resolve('New text')
    await second
  })
  expect(result.current.suggestion?.text).toBe('New text')
})

it('does not start a second operation on rapid taps', async () => {
  const pending = deferred<string>()
  jest.mocked(native.generate).mockReturnValue(pending.promise)
  const {result} = renderHook(() => useAltTextGeneration('file:///image.jpg'))
  let first!: Promise<void>
  act(() => {
    first = result.current.start('ocr')
    void result.current.start('ocr')
  })
  expect(native.generate).toHaveBeenCalledTimes(1)
  await act(async () => {
    pending.resolve('Text')
    await first
  })
})

it('does not start inference if cancelled while checking description support', async () => {
  const pending = deferred<'available'>()
  jest.mocked(native.getDescriptionStatus).mockReturnValueOnce(pending.promise)
  const {result} = renderHook(() => useAltTextGeneration('file:///image.jpg'))
  let task!: Promise<void>
  act(() => {
    task = result.current.start('describe')
  })
  expect(result.current.phase).toBe('checking')
  act(() => result.current.cancel())
  await act(async () => {
    pending.resolve('available')
    await task
  })
  expect(native.generate).not.toHaveBeenCalled()
  expect(result.current.busy).toBe(false)
  expect(result.current.suggestion).toBeNull()
})

it('cancels and removes listeners on close, ignoring completion afterwards', async () => {
  const pending = deferred<string>()
  jest.mocked(native.generate).mockReturnValue(pending.promise)
  const {result, unmount} = renderHook(() =>
    useAltTextGeneration('file:///image.jpg'),
  )
  let task!: Promise<void>
  act(() => {
    task = result.current.start('ocr')
  })
  unmount()
  expect(native.cancel).toHaveBeenCalledWith(
    jest.mocked(native.generate).mock.calls[0][0],
  )
  expect(mockRemoveListener).toHaveBeenCalledTimes(1)
  pending.resolve('Late text')
  await task
})

it('distinguishes an empty OCR result from a failed operation and allows retry', async () => {
  jest
    .mocked(native.generate)
    .mockResolvedValueOnce(' \n ')
    .mockRejectedValueOnce({code: 'ERR_BUSY'})
    .mockResolvedValueOnce('Recovered')
  const {result} = renderHook(() => useAltTextGeneration('file:///image.jpg'))
  await act(() => result.current.start('ocr'))
  expect(result.current.error).toBe('ERR_NO_TEXT')
  await act(() => result.current.start('ocr'))
  expect(result.current.error).toBe('ERR_BUSY')
  expect(result.current.busy).toBe(false)
  await act(() => result.current.start('ocr'))
  expect(result.current.error).toBeNull()
  expect(result.current.suggestion?.text).toBe('Recovered')
})
