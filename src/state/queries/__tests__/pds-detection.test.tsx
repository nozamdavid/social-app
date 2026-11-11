import {createElement} from 'react'
import {QueryClient, QueryClientProvider} from '@tanstack/react-query'
import {renderHook, waitFor} from '@testing-library/react-native'

import {
  resolvePdsForIdentifier,
  RQKEY,
  useHostingProvider,
} from '#/state/queries/pds-detection'

let mockDirectory = ''
const did = 'did:plc:alice'
const mockCall = jest.fn(() => Promise.resolve({did}))
jest.mock('#/features/infrastructure/preferences', () => ({
  getInfrastructureSettings: () => ({plcDirectoryHostname: mockDirectory}),
  useInfrastructureSettings: () => ({plcDirectoryHostname: mockDirectory}),
}))
jest.mock('#/state/session/clients', () => ({
  getPublicAppviewClient: () => ({call: mockCall}),
}))
jest.mock('#/logger', () => ({logger: {debug: jest.fn()}}))
jest.mock('#/lib/hooks/useDebouncedValue', () => ({
  useDebouncedValue: (value: string) => value,
}))

const fetchMock = jest.spyOn(global, 'fetch')
function documentResponse(id = did, pds = 'https://pds.example.com') {
  return new Response(
    JSON.stringify({
      id,
      service: [
        {
          id: '#atproto_pds',
          type: 'AtprotoPersonalDataServer',
          serviceEndpoint: pds,
        },
      ],
    }),
    {status: 200},
  )
}

beforeEach(() => {
  mockDirectory = ''
  jest.clearAllMocks()
  fetchMock.mockImplementation(() => Promise.resolve(documentResponse()))
})
afterAll(() => fetchMock.mockRestore())

it.each([
  '',
  'plc.eurosky.network',
  'plc.wafflehouse.dev',
  'plc.custom.example',
])(
  'resolves a handle via the selected PLC directory (%s) without sending credentials',
  async hostname => {
    mockDirectory = hostname
    expect(await resolvePdsForIdentifier('@ALICE.EXAMPLE.COM')).toEqual({
      did,
      pdsUrl: 'https://pds.example.com',
    })
    expect(fetchMock).toHaveBeenCalledWith(
      `https://${hostname || 'plc.directory'}/did%3Aplc%3Aalice`,
      {signal: expect.any(AbortSignal)},
    )
    expect(mockCall).toHaveBeenCalledTimes(1)
  },
)

it('reads directory changes for existing callers and bypasses handle lookup for DIDs', async () => {
  await resolvePdsForIdentifier(did)
  mockDirectory = 'plc.eurosky.network'
  await resolvePdsForIdentifier(did)
  expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
    'https://plc.directory/did%3Aplc%3Aalice',
    'https://plc.eurosky.network/did%3Aplc%3Aalice',
  ])
  expect(mockCall).not.toHaveBeenCalled()
})

it('keeps did:web resolution independent of the selected PLC directory', async () => {
  mockDirectory = 'plc.eurosky.network'
  fetchMock.mockImplementation(() =>
    Promise.resolve(documentResponse('did:web:alice.example.com')),
  )
  await resolvePdsForIdentifier('did:web:alice.example.com')
  expect(fetchMock).toHaveBeenCalledWith(
    'https://alice.example.com/.well-known/did.json',
    {signal: expect.any(AbortSignal)},
  )
})

it.each([429, 500, 503])(
  'surfaces directory failures (%s) instead of silently falling back',
  async status => {
    mockDirectory = 'plc.eurosky.network'
    fetchMock.mockImplementation(() =>
      Promise.resolve(new Response('', {status})),
    )
    await expect(resolvePdsForIdentifier(did)).rejects.toThrow(
      `PLC directory returned ${status}`,
    )
    expect(fetchMock).toHaveBeenCalledTimes(1)
  },
)

it('does not reuse cached account discovery from a different directory', async () => {
  const client = new QueryClient({defaultOptions: {queries: {retry: false}}})
  client.setQueryData(RQKEY(did, ''), {
    did,
    pdsUrl: 'https://old-pds.example.com',
  })
  const {result, rerender} = renderHook(
    () => useHostingProvider({identifier: did}),
    {
      wrapper: ({children}) =>
        createElement(QueryClientProvider, {client}, children),
    },
  )
  expect(result.current.service).toBe('https://old-pds.example.com')
  mockDirectory = 'plc.eurosky.network'
  rerender({})
  await waitFor(() =>
    expect(result.current.service).toBe('https://pds.example.com'),
  )
  expect(fetchMock).toHaveBeenCalledWith(
    'https://plc.eurosky.network/did%3Aplc%3Aalice',
    {signal: expect.any(AbortSignal)},
  )
  expect(client.getQueryData(RQKEY(did, ''))).toEqual({
    did,
    pdsUrl: 'https://old-pds.example.com',
  })
})
