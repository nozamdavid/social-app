import {useCallback} from 'react'

import {useGetEmojis} from '#/lib/useGetEmojis'
import {createEmojiSearch} from '#/features/emojiSuggestions/search'

/** Share one lazy index, including concurrent first searches. */
let searchPromise: Promise<ReturnType<typeof createEmojiSearch>> | undefined

export function useEmojiSearch() {
  const getEmojis = useGetEmojis()
  return useCallback(
    async (query: string, limit = 8, context = '') => {
      searchPromise ??= getEmojis()
        .then(createEmojiSearch)
        .catch(error => {
          searchPromise = undefined
          throw error
        })
      const search = await searchPromise
      return search(query, limit, context)
    },
    [getEmojis],
  )
}
