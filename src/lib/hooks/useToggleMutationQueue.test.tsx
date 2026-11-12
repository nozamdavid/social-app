import {act, renderHook} from '@testing-library/react-native'

import {
  createToggleMutationQueueState,
  useToggleMutationQueue,
} from '#/lib/hooks/useToggleMutationQueue'

it('settles repeated requests for the same state without repeating the write', async () => {
  let finish!: (value: string) => void
  const pending = new Promise<string>(resolve => {
    finish = resolve
  })
  const runMutation = jest.fn(() => pending)
  const {result} = renderHook(() =>
    useToggleMutationQueue({
      initialState: undefined as string | undefined,
      runMutation,
      onSuccess: jest.fn(),
    }),
  )
  await act(async () => {
    const first = result.current(true)
    const second = result.current(true)
    finish('created-record')
    expect(await first).toBe('created-record')
    expect(await second).toBe('created-record')
  })
  expect(runMutation).toHaveBeenCalledTimes(1)
})

it('serializes opposite actions from two controls sharing a resource', async () => {
  const sharedQueue = createToggleMutationQueueState<string | undefined>()
  let finish!: (value: string) => void
  const pending = new Promise<string>(resolve => {
    finish = resolve
  })
  const runMutation = jest.fn(
    async (_previous: string | undefined, on: boolean) =>
      on ? pending : undefined,
  )
  const onSuccess = jest.fn()
  const {result} = renderHook(() => ({
    first: useToggleMutationQueue({
      initialState: undefined as string | undefined,
      sharedQueue,
      runMutation,
      onSuccess,
    }),
    second: useToggleMutationQueue({
      initialState: undefined as string | undefined,
      sharedQueue,
      runMutation,
      onSuccess,
    }),
  }))
  await act(async () => {
    const liking = result.current.first(true)
    const unliking = result.current.second(false)
    finish('real-record')
    await Promise.all([liking, unliking])
  })
  expect(runMutation.mock.calls).toEqual([
    [undefined, true],
    ['real-record', false],
  ])
  expect(onSuccess).toHaveBeenLastCalledWith(undefined)
})
