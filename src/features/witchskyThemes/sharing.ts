import {type Client} from '@atproto/lex'
import {toDatetimeString} from '@atproto/syntax'
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query'

import {createServiceClient} from '#/lib/lexClient'
import {isNetworkError} from '#/lib/strings/errors'
import {logger} from '#/logger'
import {STALE} from '#/state/queries'
import {resolvePdsForIdentifier} from '#/state/queries/pds-detection'
import {createQueryKey} from '#/state/queries/util'
import {usePdsClient, useSession} from '#/state/session'
import {useInfrastructureSettings} from '#/features/infrastructure/preferences'
import {com} from '#/lexicons'
import {witchskyThemesQueryKeyRoot} from './queries'
import {parseThemeRecord, THEME_COLLECTION, type ThemeOption} from './schema'

type SourceRecord = com.atproto.repo.listRecords.$OutputBody['records'][number]
export type SharedTheme = Pick<SourceRecord, 'uri' | 'value'> & {
  options: ThemeOption[]
}

/** Read public theme records directly from their owner's PDS, without session credentials. */
export async function fetchSharedThemes(
  identifier: string,
  directory: string,
  signal?: AbortSignal,
): Promise<SharedTheme[]> {
  const identity = await resolvePdsForIdentifier(identifier, directory)
  if (!identity?.pdsUrl) throw new Error('Could not resolve theme owner')
  const client = createServiceClient(identity.pdsUrl)
  const themes = new Map<string, SharedTheme>()
  const seen = new Set<string>()
  let cursor: string | undefined
  do {
    const page = await client.call(
      com.atproto.repo.listRecords,
      {
        repo: identity.did as `did:${string}:${string}`,
        collection: THEME_COLLECTION,
        limit: 100,
        cursor,
      },
      {signal},
    )
    for (const record of page.records) {
      const options = parseThemeRecord(record.uri, record.value)
      if (options.length)
        themes.set(record.uri, {uri: record.uri, value: record.value, options})
    }
    if (!page.records.length) break
    cursor = page.cursor
    if (cursor && seen.has(cursor)) throw new Error('Theme pagination stalled')
    if (cursor) seen.add(cursor)
  } while (cursor)
  return [...themes.values()]
}

const accountThemesQueryKeyRoot = 'accountThemes'
export function useAccountThemesQuery(identifier: string) {
  const {plcDirectoryHostname} = useInfrastructureSettings()
  return useQuery({
    queryKey: createQueryKey(accountThemesQueryKeyRoot, {
      identifier,
      plcDirectoryHostname,
    }),
    enabled: !!identifier,
    staleTime: STALE.MINUTES.ONE,
    retry: false,
    queryFn: ({signal}) =>
      fetchSharedThemes(identifier, plcDirectoryHostname, signal),
  })
}

/** Copy the entire source record, preserving its variants and leaving the source untouched. */
export async function copySharedTheme(
  client: Client,
  did: `did:${string}:${string}`,
  theme: SharedTheme,
) {
  if (!parseThemeRecord(theme.uri, theme.value).length)
    throw new Error('Invalid theme record')
  return client.call(com.atproto.repo.createRecord, {
    repo: did,
    collection: THEME_COLLECTION,
    record: {...theme.value, createdAt: toDatetimeString(new Date())},
    validate: false,
  })
}

export function useAddThemeMutation() {
  const client = usePdsClient()
  const {currentAccount} = useSession()
  const qc = useQueryClient()
  return useMutation({
    async mutationFn(theme: SharedTheme) {
      if (!currentAccount) throw new Error('No active account')
      await copySharedTheme(client, currentAccount.did, theme)
    },
    async onSuccess() {
      await qc.invalidateQueries({queryKey: [witchskyThemesQueryKeyRoot]})
    },
    onError(error) {
      if (!isNetworkError(error))
        logger.error('Failed adding shared theme', {safeMessage: error})
    },
  })
}
