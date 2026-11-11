import {type AtUriString, toDatetimeString} from '@atproto/syntax'
import {useMutation, useQueryClient} from '@tanstack/react-query'

import {isNetworkError} from '#/lib/strings/errors'
import {logger} from '#/logger'
import {updatePostShadow} from '#/state/cache/post-shadow'
import {
  optimisticallyDeleteBookmark,
  optimisticallySaveBookmark,
} from '#/state/queries/bookmarks/useBookmarksQuery'
import {useAppviewClient} from '#/state/session'
import {useBookmarkScope} from '#/features/searchableBookmarks/hooks'
import {update} from '#/features/searchableBookmarks/store'
import {app} from '#/lexicons'

type MutationArgs =
  | {action: 'create'; post: app.bsky.feed.defs.PostView}
  | {
      action: 'delete'
      /**
       * For deletions, we only need to URI. Plus, in some cases we only know the
       * URI, such as when a post was deleted by the author.
       */
      uri: string
    }

export function useBookmarkMutation() {
  const qc = useQueryClient()
  const {enabled, key} = useBookmarkScope()
  const client = useAppviewClient()

  return useMutation({
    async mutationFn(args: MutationArgs) {
      if (args.action === 'create') {
        updatePostShadow(qc, args.post.uri, {bookmarked: true})
        await client.call(app.bsky.bookmark.createBookmark, {
          uri: args.post.uri,
          cid: args.post.cid,
        })
      } else if (args.action === 'delete') {
        updatePostShadow(qc, args.uri, {bookmarked: false})
        await client.call(app.bsky.bookmark.deleteBookmark, {
          uri: args.uri as AtUriString,
        })
      }
      return {key, enabled}
    },
    async onSuccess({key, enabled}, args) {
      if (args.action === 'create') {
        optimisticallySaveBookmark(qc, args.post)
      } else if (args.action === 'delete') {
        optimisticallyDeleteBookmark(qc, {uri: args.uri})
      }
      if (enabled) {
        try {
          if (args.action === 'create') {
            await update(key, args.post.uri, {
              createdAt: toDatetimeString(new Date()),
              subject: {uri: args.post.uri, cid: args.post.cid},
              item: {...args.post, $type: 'app.bsky.feed.defs#postView'},
            })
          } else {
            await update(key, args.uri)
          }
          await qc.invalidateQueries({queryKey: ['bookmarks', 'local', key]})
        } catch (error) {
          logger.error('Failed updating local saved posts', {
            safeMessage: error,
          })
        }
      }
    },
    onError(e, args) {
      if (args.action === 'create') {
        updatePostShadow(qc, args.post.uri, {bookmarked: false})
      } else if (args.action === 'delete') {
        updatePostShadow(qc, args.uri, {bookmarked: true})
      }

      if (!isNetworkError(e)) {
        logger.error('bookmark mutation failed', {
          bookmarkAction: args.action,
          safeMessage: e,
        })
      }
    },
  })
}
