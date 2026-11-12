import {useState} from 'react'
import {View} from 'react-native'
import {RichText} from '@bsky/sdk/richtext'
import {Trans, useLingui} from '@lingui/react/macro'

import {MAX_GRAPHEME_LENGTH} from '#/lib/constants'
import {cleanError} from '#/lib/strings/errors'
import {shortenLinks} from '#/lib/strings/rich-text-manip'
import {type Shadow} from '#/state/cache/post-shadow'
import {usePostEditMutation} from '#/state/queries/post-edit'
import {CharProgress} from '#/view/com/composer/char-progress/CharProgress'
import {atoms as a, useTheme, web} from '#/alf'
import {Admonition} from '#/components/Admonition'
import {Button, ButtonIcon, ButtonText} from '#/components/Button'
import * as Dialog from '#/components/Dialog'
import * as TextField from '#/components/forms/TextField'
import {Loader} from '#/components/Loader'
import * as Toast from '#/components/Toast'
import {Text} from '#/components/Typography'
import {type app} from '#/lexicons'

const MAX_POST_BYTES = 3000

export function EditPostDialog({
  control,
  post,
  record,
}: {
  control: Dialog.DialogControlProps
  post: Shadow<app.bsky.feed.defs.PostView>
  record: app.bsky.feed.post.Main
}) {
  return (
    <Dialog.Outer control={control} nativeOptions={{preventExpansion: false}}>
      <Dialog.Handle />
      <EditPostDialogInner control={control} post={post} record={record} />
    </Dialog.Outer>
  )
}

function EditPostDialogInner({
  control,
  post,
  record,
}: {
  control: Dialog.DialogControlProps
  post: Shadow<app.bsky.feed.defs.PostView>
  record: app.bsky.feed.post.Main
}) {
  const t = useTheme()
  const {t: l} = useLingui()
  const [text, setText] = useState(record.text)
  const [error, setError] = useState('')
  const {mutateAsync: editPostMutate, isPending} = usePostEditMutation()

  const trimmedText = text.replace(/^(\s*\n)+/, '').trimEnd()
  const shortenedRt = shortenLinks(new RichText({text: trimmedText}))
  const graphemeCount = shortenedRt.graphemeLength
  const isOverLimit =
    graphemeCount > MAX_GRAPHEME_LENGTH || shortenedRt.length > MAX_POST_BYTES
  const isEmptyWithoutEmbed = !trimmedText && !record.embed
  const isUnchanged = trimmedText === record.text
  const canSave =
    !isPending && !isOverLimit && !isEmptyWithoutEmbed && !isUnchanged

  const onPressSave = async () => {
    if (!canSave) return
    setError('')
    try {
      await editPostMutate({
        uri: post.uri,
        expectedCid: post.cid,
        text,
      })
      control.close(() => {
        Toast.show(l`Post updated`, {type: 'success'})
      })
    } catch (err) {
      setError(cleanError(err))
    }
  }

  return (
    <Dialog.ScrollableInner
      label={l`Edit post`}
      style={[web({maxWidth: 500}), a.w_full]}
      keyboardDismissMode="on-drag">
      <View style={[a.gap_md]}>
        <View style={[a.flex_row, a.align_center, a.justify_between]}>
          <Button
            testID="editPostCancelBtn"
            label={l`Cancel`}
            size="small"
            variant="ghost"
            color="secondary"
            disabled={isPending}
            onPress={() => control.close()}>
            <ButtonText>
              <Trans>Cancel</Trans>
            </ButtonText>
          </Button>
          <Text style={[a.text_lg, a.font_bold]}>
            <Trans>Edit post</Trans>
          </Text>
          <Button
            testID="editPostSaveBtn"
            label={l`Save`}
            size="small"
            color="primary"
            disabled={!canSave}
            onPress={() => void onPressSave()}>
            {isPending && <ButtonIcon icon={Loader} />}
            <ButtonText>
              <Trans>Save</Trans>
            </ButtonText>
          </Button>
        </View>

        {error ? <Admonition type="error">{error}</Admonition> : null}

        <TextField.Root isInvalid={isOverLimit}>
          <Dialog.Input
            testID="editPostTextInput"
            multiline
            autoFocus
            value={text}
            onChangeText={nextText => {
              setText(nextText)
              if (error) setError('')
            }}
            label={l`Post text`}
            placeholder={l`What’s up?`}
            style={[{minHeight: 120, textAlignVertical: 'top'}]}
          />
        </TextField.Root>

        {record.embed ? (
          <Text style={[a.text_xs, t.atoms.text_contrast_medium]}>
            <Trans>Existing attachments will stay on this post.</Trans>
          </Text>
        ) : null}

        <View style={[a.flex_row, a.align_center, a.justify_between, a.gap_md]}>
          <View style={[a.flex_1]}>
            <Text style={[a.text_xs, t.atoms.text_contrast_medium]}>
              <Trans>Posts can be edited once within 10 minutes.</Trans>
            </Text>
          </View>
          <CharProgress count={graphemeCount} style={{width: 65}} />
        </View>
      </View>
      <Dialog.Close />
    </Dialog.ScrollableInner>
  )
}
