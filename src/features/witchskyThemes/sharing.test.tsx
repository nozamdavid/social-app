import {type Client} from '@atproto/lex'
import {QueryClient, QueryClientProvider} from '@tanstack/react-query'
import {act, renderHook, waitFor} from '@testing-library/react-native'

import {createServiceClient} from '#/lib/lexClient'
import {resolvePdsForIdentifier} from '#/state/queries/pds-detection'
import {com} from '#/lexicons'
import {parseThemeRecord, THEME_COLLECTION} from './schema'
import {
  copySharedTheme,
  fetchSharedThemes,
  useAccountThemesQuery,
  useAddThemeMutation,
} from './sharing'

const mockPublicCall = jest.fn()
const mockPrivateCall = jest.fn()
let mockDid: `did:${string}:${string}` | undefined = 'did:plc:mine'
jest.mock('#/lib/lexClient', () => ({
  createServiceClient: jest.fn(() => ({call: mockPublicCall})),
}))
jest.mock('#/state/queries/pds-detection', () => ({
  resolvePdsForIdentifier: jest.fn(),
}))
jest.mock('#/state/session', () => ({
  usePdsClient: () => ({call: mockPrivateCall}),
  useSession: () => ({currentAccount: mockDid ? {did: mockDid} : undefined}),
}))
jest.mock('#/features/infrastructure/preferences', () => ({
  useInfrastructureSettings: () => ({plcDirectoryHostname: ''}),
}))
jest.mock('#/logger', () => ({logger: {error: jest.fn()}}))

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
const source = {
  uri: `at://did:plc:other/${THEME_COLLECTION}/example` as const,
  value: {
    $type: THEME_COLLECTION,
    name: 'Shared theme',
    mode: 'dark',
    base: {name: 'Base', colors},
    variants: [{name: 'Light', colors: {canvas: '#ffffff'}}],
    createdAt: '2026-11-09T00:00:00Z',
  },
}
const shared = {...source, options: parseThemeRecord(source.uri, source.value)}
const privateClient = {call: mockPrivateCall} as unknown as Client

function setup() {
  const client = new QueryClient({defaultOptions: {queries: {retry: false}}})
  function Wrapper({children}: {children: React.ReactNode}) {
    return <QueryClientProvider client={client}>{children}</QueryClientProvider>
  }
  return {client, wrapper: Wrapper}
}

beforeEach(() => {
  jest.clearAllMocks()
  mockPublicCall.mockReset()
  mockPrivateCall.mockReset()
  mockDid = 'did:plc:mine'
  jest
    .mocked(resolvePdsForIdentifier)
    .mockResolvedValue({did: 'did:plc:other', pdsUrl: 'https://other.example'})
})

it('reads every public page from the resolved PDS and ignores invalid records', async () => {
  mockPublicCall
    .mockResolvedValueOnce({
      records: [source, {uri: 'invalid', value: {}}],
      cursor: 'next',
    })
    .mockResolvedValueOnce({records: [], cursor: 'next'})
  const result = await fetchSharedThemes('alice.test', 'plc.example')
  expect(resolvePdsForIdentifier).toHaveBeenCalledWith(
    'alice.test',
    'plc.example',
  )
  expect(createServiceClient).toHaveBeenCalledWith('https://other.example')
  expect(mockPublicCall).toHaveBeenNthCalledWith(
    2,
    com.atproto.repo.listRecords,
    expect.objectContaining({
      repo: 'did:plc:other',
      collection: THEME_COLLECTION,
      cursor: 'next',
    }),
    expect.anything(),
  )
  expect(result).toEqual([shared])
  expect(result[0].options).toHaveLength(2)
  expect(mockPrivateCall).not.toHaveBeenCalled()
})

it('returns an empty list for an account without themes', async () => {
  mockPublicCall.mockResolvedValueOnce({records: []})
  expect(await fetchSharedThemes('alice.test', '')).toEqual([])
})

it('fails without reading or writing if the account has no resolvable PDS', async () => {
  jest.mocked(resolvePdsForIdentifier).mockResolvedValueOnce(null)
  await expect(fetchSharedThemes('missing.test', '')).rejects.toThrow(
    'Could not resolve',
  )
  expect(mockPublicCall).not.toHaveBeenCalled()
  expect(mockPrivateCall).not.toHaveBeenCalled()
})

it('guards against stalled pagination', async () => {
  mockPublicCall.mockResolvedValue({records: [source], cursor: 'same'})
  await expect(fetchSharedThemes('alice.test', '')).rejects.toThrow(
    'pagination stalled',
  )
  expect(mockPublicCall).toHaveBeenCalledTimes(2)
})

it('copies the whole theme and variants into the signed-in account with a fresh timestamp', async () => {
  mockPrivateCall.mockResolvedValueOnce({
    uri: 'at://did:plc:mine/app.witchsky.theme.colors/new',
  })
  await copySharedTheme(privateClient, 'did:plc:mine', shared)
  expect(mockPrivateCall).toHaveBeenCalledWith(com.atproto.repo.createRecord, {
    repo: 'did:plc:mine',
    collection: THEME_COLLECTION,
    validate: false,
    record: {...source.value, createdAt: expect.any(String)},
  })
  expect(mockPrivateCall).not.toHaveBeenCalledWith(
    expect.anything(),
    expect.objectContaining({
      record: expect.objectContaining({createdAt: source.value.createdAt}),
    }),
  )
  expect(source.value.createdAt).toBe('2026-11-09T00:00:00Z')
  expect(mockPublicCall).not.toHaveBeenCalled()
})

it('refuses to copy invalid records', async () => {
  await expect(
    copySharedTheme(privateClient, 'did:plc:mine', {...shared, value: {}}),
  ).rejects.toThrow('Invalid theme')
  expect(mockPrivateCall).not.toHaveBeenCalled()
})

it('waits for a submitted account and isolates lookups by handle', async () => {
  mockPublicCall.mockResolvedValue({records: [source]})
  const {wrapper} = setup()
  const hook = renderHook(
    ({handle}: {handle: string}) => useAccountThemesQuery(handle),
    {initialProps: {handle: ''}, wrapper},
  )
  expect(mockPublicCall).not.toHaveBeenCalled()
  hook.rerender({handle: 'alice.test'})
  await waitFor(() => expect(hook.result.current.isSuccess).toBe(true))
  mockPublicCall.mockResolvedValue({records: []})
  hook.rerender({handle: 'bob.test'})
  await waitFor(() => expect(hook.result.current.data).toEqual([]))
  expect(resolvePdsForIdentifier).toHaveBeenLastCalledWith('bob.test', '')
})

it('updates the saved themes query after adding a theme', async () => {
  mockPrivateCall.mockResolvedValueOnce({
    uri: 'at://did:plc:mine/app.witchsky.theme.colors/new',
  })
  const {client, wrapper} = setup()
  const invalidate = jest.spyOn(client, 'invalidateQueries')
  const hook = renderHook(useAddThemeMutation, {wrapper})
  await act(async () => {
    await hook.result.current.mutateAsync(shared)
  })
  expect(invalidate).toHaveBeenCalledWith({queryKey: ['witchskyThemes']})
  await waitFor(() => expect(hook.result.current.isSuccess).toBe(true))
})

it('never writes while signed out', async () => {
  mockDid = undefined
  const {wrapper} = setup()
  const hook = renderHook(useAddThemeMutation, {wrapper})
  await act(async () => {
    await expect(hook.result.current.mutateAsync(shared)).rejects.toThrow(
      'No active account',
    )
  })
  expect(mockPrivateCall).not.toHaveBeenCalled()
})
