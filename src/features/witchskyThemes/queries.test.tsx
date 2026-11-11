import {QueryClient, QueryClientProvider} from '@tanstack/react-query'
import {act, renderHook, waitFor} from '@testing-library/react-native'

import {useWitchskyThemesQuery} from '#/features/witchskyThemes/queries'

let mockDid: string | undefined = 'did:plc:current'
const mockCall = jest.fn()
jest.mock('#/state/session', () => ({
  usePdsClient: () => ({call: mockCall}),
  useSession: () => ({
    currentAccount: mockDid ? {did: mockDid} : undefined,
    hasSession: !!mockDid,
  }),
}))
jest.mock('#/lexicons', () => ({com: {atproto: {repo: {listRecords: {}}}}}))

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
const record = {
  uri: 'at://did:plc:current/app.witchsky.theme.colors/one',
  value: {
    $type: 'app.witchsky.theme.colors',
    name: 'Example',
    mode: 'dark',
    base: {name: 'Base', colors},
    createdAt: '2026-11-09T00:00:00Z',
  },
}

function createWrapper() {
  const client = new QueryClient({defaultOptions: {queries: {retry: false}}})
  return function Wrapper({children}: {children: React.ReactNode}) {
    return <QueryClientProvider client={client}>{children}</QueryClientProvider>
  }
}

beforeEach(() => {
  mockCall.mockReset()
  mockDid = 'did:plc:current'
})

it('reads the current account’s PDS collection, isolates invalid records and paginates', async () => {
  mockCall
    .mockResolvedValueOnce({
      records: [record, {uri: 'invalid', value: {}}],
      cursor: 'next',
    })
    .mockResolvedValueOnce({records: [], cursor: 'next'})
  const {result} = renderHook(() => useWitchskyThemesQuery(), {
    wrapper: createWrapper(),
  })
  await waitFor(() => expect(result.current.isSuccess).toBe(true))
  expect(mockCall).toHaveBeenCalledWith(expect.anything(), {
    repo: mockDid,
    collection: 'app.witchsky.theme.colors',
    limit: 100,
    cursor: undefined,
  })
  expect(result.current.data?.pages[0]).toMatchObject({
    themes: [{name: 'Example'}],
    skipped: 1,
  })
  expect(result.current.hasNextPage).toBe(true)
  await act(async () => {
    await result.current.fetchNextPage()
  })
  await waitFor(() => expect(result.current.hasNextPage).toBe(false))
  expect(mockCall).toHaveBeenNthCalledWith(
    2,
    expect.anything(),
    expect.objectContaining({cursor: 'next'}),
  )
})

it('does not fetch signed out and reports a recoverable server error', async () => {
  mockDid = undefined
  const hook = renderHook(() => useWitchskyThemesQuery(), {
    wrapper: createWrapper(),
  })
  expect(mockCall).not.toHaveBeenCalled()
  mockCall.mockRejectedValue(new Error('PDS unavailable'))
  mockDid = 'did:plc:current'
  hook.rerender({})
  await waitFor(() => expect(hook.result.current.isError).toBe(true))
  expect(hook.result.current.data).toBeUndefined()
})
