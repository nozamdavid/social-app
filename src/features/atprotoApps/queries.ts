import {useQuery} from '@tanstack/react-query'

import {createServiceClient} from '#/lib/lexClient'
import {isNetworkError} from '#/lib/strings/errors'
import {logger} from '#/logger'
import {STALE} from '#/state/queries'
import {resolvePdsForIdentifier} from '#/state/queries/pds-detection'
import {createQueryKey} from '#/state/queries/util'
import {useInfrastructureSettings} from '#/features/infrastructure/preferences'
import {com} from '#/lexicons'
import {PUBLICATION_COLLECTION} from './apps'
import {
  type DiscoveredApp,
  getAppsFromCollections,
  hasPublications,
  parsePublication,
} from './discovery'

/** Upper bound on publication pages fetched, to stay bounded on odd repos. */
const MAX_PUBLICATION_PAGES = 5

/**
 * Find other atproto apps a user uses by inspecting their repo directly on
 * their PDS, without session credentials.
 */
export async function fetchAtprotoApps({
  did,
  handle,
  directory,
  signal,
}: {
  did: string
  handle?: string
  directory: string
  signal?: AbortSignal
}): Promise<DiscoveredApp[]> {
  const identity = await resolvePdsForIdentifier(did, directory)
  if (!identity?.pdsUrl) return []
  const {pdsUrl} = identity
  const client = createServiceClient(pdsUrl)
  const repo = did as `did:${string}:${string}`

  const description = await client.call(
    com.atproto.repo.describeRepo,
    {repo},
    {signal},
  )
  const apps = getAppsFromCollections({
    collections: description.collections,
    did,
    handle,
  })

  if (hasPublications(description.collections)) {
    let cursor: string | undefined
    for (let page = 0; page < MAX_PUBLICATION_PAGES; page++) {
      const res = await client.call(
        com.atproto.repo.listRecords,
        {repo, collection: PUBLICATION_COLLECTION, limit: 100, cursor},
        {signal},
      )
      for (const record of res.records) {
        const rkey = record.uri.split('/').pop() ?? ''
        const app = parsePublication({
          rkey,
          value: record.value,
          did,
          pdsUrl,
        })
        if (app) apps.push(app)
      }
      cursor = res.cursor
      if (!cursor || !res.records.length) break
    }
  }
  return apps
}

export const atprotoAppsQueryKeyRoot = 'atprotoApps'

export function useAtprotoAppsQuery({
  did,
  handle,
  enabled = true,
}: {
  did: string
  handle?: string
  enabled?: boolean
}) {
  const {plcDirectoryHostname} = useInfrastructureSettings()
  return useQuery({
    queryKey: createQueryKey(atprotoAppsQueryKeyRoot, {
      did,
      handle,
      plcDirectoryHostname,
    }),
    enabled,
    staleTime: STALE.MINUTES.FIVE,
    retry: false,
    queryFn: async ({signal}) => {
      try {
        return await fetchAtprotoApps({
          did,
          handle,
          directory: plcDirectoryHostname,
          signal,
        })
      } catch (err) {
        if (!isNetworkError(err)) {
          logger.error('Failed to discover atproto apps', {safeMessage: err})
        }
        throw err
      }
    },
  })
}
