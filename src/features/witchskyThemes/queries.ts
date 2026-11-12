import {useInfiniteQuery} from '@tanstack/react-query'

import {STALE} from '#/state/queries'
import {createQueryKey} from '#/state/queries/util'
import {usePdsClient, useSession} from '#/state/session'
import {
  parseThemeRecord,
  THEME_COLLECTION,
} from '#/features/witchskyThemes/schema'
import {com} from '#/lexicons'

export const witchskyThemesQueryKeyRoot = 'witchskyThemes'

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
      let skipped = parsed.filter(options => options.length === 0).length
      let nextCursor = page.cursor
      const seen = new Set(pageParam ? [pageParam] : [])
      /* A PDS can return a cursor on its last page. Only offer another page
       * after confirming it contains a loadable theme. Skip invalid-only pages.
       */
      while (nextCursor) {
        if (seen.has(nextCursor)) {
          nextCursor = undefined
          break
        }
        seen.add(nextCursor)
        const next = await pdsClient.call(com.atproto.repo.listRecords, {
          repo: did!,
          collection: THEME_COLLECTION,
          limit: 100,
          cursor: nextCursor,
        })
        const nextThemes = next.records.map(record =>
          parseThemeRecord(record.uri, record.value),
        )
        if (nextThemes.some(options => options.length > 0)) break
        skipped += nextThemes.length
        nextCursor = next.cursor
      }
      return {
        cursor: nextCursor,
        themes: parsed.flat(),
        skipped,
      }
    },
    getNextPageParam: (page, _pages, lastParam, params) =>
      page.cursor && page.cursor !== lastParam && !params.includes(page.cursor)
        ? page.cursor
        : undefined,
  })
}
