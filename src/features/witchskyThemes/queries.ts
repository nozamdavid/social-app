import {useInfiniteQuery} from '@tanstack/react-query'

import {STALE} from '#/state/queries'
import {createQueryKey} from '#/state/queries/util'
import {usePdsClient, useSession} from '#/state/session'
import {
  parseThemeRecord,
  THEME_COLLECTION,
} from '#/features/witchskyThemes/schema'
import {com} from '#/lexicons'

const witchskyThemesQueryKeyRoot = 'witchskyThemes'

/** Fetch from the account's PDS, where third-party records are available. */
export function useWitchskyThemesQuery() {
  const pdsClient = usePdsClient()
  const {currentAccount, hasSession} = useSession()
  const did = currentAccount?.did
  return useInfiniteQuery({
    queryKey: createQueryKey(witchskyThemesQueryKeyRoot, {did}),
    enabled: hasSession && !!did,
    staleTime: STALE.MINUTES.ONE,
    initialPageParam: undefined as string | undefined,
    async queryFn({pageParam}) {
      const page = await pdsClient.call(com.atproto.repo.listRecords, {
        repo: did!,
        collection: THEME_COLLECTION,
        limit: 100,
        cursor: pageParam,
      })
      const parsed = page.records.map(record =>
        parseThemeRecord(record.uri, record.value),
      )
      return {
        cursor: page.cursor,
        themes: parsed.flat(),
        skipped: parsed.filter(options => options.length === 0).length,
      }
    },
    getNextPageParam: (page, _pages, lastParam, params) =>
      page.cursor && page.cursor !== lastParam && !params.includes(page.cursor)
        ? page.cursor
        : undefined,
  })
}
