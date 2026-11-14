import {useQuery} from '@tanstack/react-query'

import {useDebouncedValue} from '#/lib/hooks/useDebouncedValue'
import {STALE} from '#/state/queries'
import {createQueryKey} from '#/state/queries/util'

type SuggestedAccount = {
  did: string
  handle: string
  avatar?: string
  displayName?: string
}

const useUsernameSuggestionsQueryRoot = 'login-username-suggestions'

export function useUsernameSuggestionsQuery(
  identifier: string,
  enabled: boolean,
) {
  const prefix = identifier.trim().replace(/^@/, '').toLowerCase()
  const searchable =
    prefix.length >= 2 &&
    !prefix.includes('@') &&
    !prefix.startsWith('did:') &&
    !/\s/.test(prefix)
  const query = searchable && enabled ? prefix : ''
  const debouncedQuery = useDebouncedValue(query, 300)
  const result = useQuery({
    queryKey: createQueryKey(useUsernameSuggestionsQueryRoot, {
      query: debouncedQuery,
    }),
    enabled: !!query && query === debouncedQuery,
    staleTime: STALE.MINUTES.ONE,
    gcTime: 0,
    retry: false,
    queryFn: async ({signal}): Promise<SuggestedAccount[]> => {
      const response = await fetch(
        `https://typeahead.waow.tech/xrpc/tech.waow.typeahead.searchActors?q=${encodeURIComponent(debouncedQuery)}&limit=5`,
        {signal, headers: {'X-Client': 'bsky.app'}},
      )
      if (!response.ok) throw new Error('Username suggestions unavailable')
      const data: unknown = await response.json()
      if (
        !data ||
        typeof data !== 'object' ||
        !('actors' in data) ||
        !Array.isArray(data.actors)
      ) {
        throw new Error('Invalid username suggestions response')
      }
      return data.actors
        .filter(
          (actor: unknown): actor is SuggestedAccount =>
            !!actor &&
            typeof actor === 'object' &&
            'did' in actor &&
            'handle' in actor &&
            typeof actor.did === 'string' &&
            typeof actor.handle === 'string' &&
            (!('avatar' in actor) ||
              actor.avatar === undefined ||
              typeof actor.avatar === 'string') &&
            /^[a-z0-9-]+(?:\.[a-z0-9-]+)+$/i.test(actor.handle) &&
            (!('displayName' in actor) ||
              actor.displayName === undefined ||
              typeof actor.displayName === 'string'),
        )
        .slice(0, 5)
    },
  })

  return query && query === debouncedQuery ? (result.data ?? []) : []
}
