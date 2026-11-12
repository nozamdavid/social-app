import {type LexMap} from '@atproto/lex'
import {AtUri} from '@atproto/syntax'
import {RichText} from '@bsky/sdk/richtext'
import {useLingui} from '@lingui/react/macro'
import {useMutation, useQueryClient} from '@tanstack/react-query'

import {MAX_GRAPHEME_LENGTH} from '#/lib/constants'
import {isNetworkError} from '#/lib/strings/errors'
import {shortenLinks, stripInvalidMentions} from '#/lib/strings/rich-text-manip'
import {logger} from '#/logger'
import {updatePostShadow} from '#/state/cache/post-shadow'
import {useAppviewClient, usePdsClient} from '#/state/session'
import {type app, com} from '#/lexicons'
import {buildEditedPostRecord, isPostEditable} from './post-edit-utils'

export {isPostEditable, POST_EDIT_WINDOW_MS} from './post-edit-utils'

const MAX_POST_BYTES = 3000

export function usePostEditMutation() {
  const queryClient = useQueryClient()
  const pdsClient = usePdsClient()
  const appviewClient = useAppviewClient()
  const {t: l} = useLingui()

  return useMutation<
    app.bsky.feed.post.Main,
    Error,
    {uri: string; expectedCid?: string; text: string}
  >({
    mutationFn: async ({uri, expectedCid, text}) => {
      const did = pdsClient.assertDid
      const urip = new AtUri(uri)
      if (
        urip.host !== did ||
        urip.collection !== 'app.bsky.feed.post' ||
        !urip.rkey
      ) {
        throw new Error(l`You can only edit your own posts.`)
      }

      const current = await pdsClient.call(com.atproto.repo.getRecord, {
        repo: did,
        collection: 'app.bsky.feed.post',
        rkey: urip.rkey,
      })

      const currentValue = current.value
      if (
        !currentValue ||
        currentValue.$type !== 'app.bsky.feed.post' ||
        typeof currentValue.text !== 'string'
      ) {
        throw new Error(l`This post could not be loaded for editing.`)
      }

      if (expectedCid && current.cid !== expectedCid) {
        throw new Error(
          l`This post changed elsewhere and can no longer be edited.`,
        )
      }

      if (!isPostEditable(currentValue)) {
        throw new Error(l`This post can no longer be edited.`)
      }

      const trimmedText = text.replace(/^(\s*\n)+/, '').trimEnd()
      let rt = new RichText({text: trimmedText}, {cleanNewlines: true})
      await rt.detectFacets(appviewClient)
      rt = shortenLinks(rt)
      rt = stripInvalidMentions(rt)

      if (
        rt.graphemeLength > MAX_GRAPHEME_LENGTH ||
        rt.length > MAX_POST_BYTES
      ) {
        throw new Error(l`Post is too long.`)
      }

      if (!rt.text && !currentValue.embed) {
        throw new Error(l`Post cannot be empty.`)
      }

      if (rt.text === currentValue.text) {
        throw new Error(l`Make a change to your post before saving.`)
      }

      const updatedRecord = buildEditedPostRecord({
        currentRecord: currentValue as LexMap & {text: string},
        resolvedText: rt.text,
        resolvedFacets: rt.facets,
      })

      try {
        await pdsClient.call(com.atproto.repo.applyWrites, {
          repo: did,
          validate: true,
          writes: [
            {
              $type: 'com.atproto.repo.applyWrites#delete',
              collection: 'app.bsky.feed.post',
              rkey: urip.rkey,
            },
            {
              $type: 'com.atproto.repo.applyWrites#create',
              collection: 'app.bsky.feed.post',
              rkey: urip.rkey,
              value: updatedRecord,
            },
          ],
        })
      } catch (err) {
        const e = err as Error
        if (isNetworkError(e)) {
          throw new Error(
            l`Post edit failed. Please check your internet connection and try again.`,
          )
        }
        logger.error('Failed to edit post', {safeMessage: e.message})
        throw e
      }

      return updatedRecord as app.bsky.feed.post.Main
    },
    onSuccess(updatedRecord, variables) {
      updatePostShadow(queryClient, variables.uri, {
        record: updatedRecord,
      })
    },
  })
}
