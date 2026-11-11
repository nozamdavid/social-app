import {type AtUriString} from '@atproto/syntax'
import {type QueryClient} from '@tanstack/react-query'

import {createToggleMutationQueueState} from '#/lib/hooks/useToggleMutationQueue'

type LikeQueue = ReturnType<
  typeof createToggleMutationQueueState<AtUriString | undefined>
>
const queues = new WeakMap<QueryClient, Map<string, LikeQueue>>()

/** Manual and automatic likes must serialize, including an unlike while liking. */
export function getPostLikeQueue(client: QueryClient, uri: string) {
  let posts = queues.get(client)
  if (!posts) {
    posts = new Map()
    queues.set(client, posts)
  }
  let queue = posts.get(uri)
  if (!queue) {
    queue = createToggleMutationQueueState<AtUriString | undefined>()
    posts.set(uri, queue)
  }
  return queue
}
