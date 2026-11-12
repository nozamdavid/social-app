import {useState} from 'react'
import {Keyboard, View} from 'react-native'
import {isValidHandle} from '@atproto/syntax'
import {Trans, useLingui} from '@lingui/react/macro'

import {useSession} from '#/state/session'
import {atoms as a, useTheme, web} from '#/alf'
import {Button, ButtonIcon, ButtonText} from '#/components/Button'
import * as Dialog from '#/components/Dialog'
import * as TextField from '#/components/forms/TextField'
import {Check_Stroke2_Corner0_Rounded as CheckIcon} from '#/components/icons/Check'
import {Eye_Stroke2_Corner0_Rounded as EyeIcon} from '#/components/icons/Eye'
import {PlusLarge_Stroke2_Corner0_Rounded as PlusIcon} from '#/components/icons/Plus'
import {Loader} from '#/components/Loader'
import {Text} from '#/components/Typography'
import {IS_NATIVE} from '#/env'
import {groupThemes} from './groups'
import {THEME_COLLECTION} from './schema'
import {
  type SharedTheme,
  useAccountThemesQuery,
  useAddThemeMutation,
} from './sharing'
import {ThemePreview} from './ThemePreview'
import {ThemePreviewModal} from './ThemePreviewModal'

export function AddThemesDialog() {
  const control = Dialog.useDialogControl()
  const {t: l} = useLingui()
  return (
    <>
      <Button
        label={l`Add themes`}
        color="secondary"
        size="small"
        style={{minHeight: 48}}
        onPress={control.open}
        testID="witchsky-themes-add">
        <ButtonText>
          <Trans>Add themes</Trans>
        </ButtonText>
      </Button>
      <Dialog.Outer control={control}>
        <Dialog.Handle />
        <AddThemesInner />
      </Dialog.Outer>
    </>
  )
}

function AddThemesInner() {
  const control = Dialog.useDialogContext()
  const t = useTheme()
  const {t: l} = useLingui()
  const [handle, setHandle] = useState('')
  const [submitted, setSubmitted] = useState('')
  const normalized = handle.trim().replace(/^@/, '').toLowerCase()
  const valid = isValidHandle(normalized)
  const query = useAccountThemesQuery(submitted)
  const themes = query.data ?? []
  const submit = () => {
    if (!valid || query.isFetching) return
    Keyboard.dismiss()
    if (normalized === submitted) void query.refetch()
    else setSubmitted(normalized)
  }
  return (
    <Dialog.ScrollableInner label={l`Add themes`} style={web({maxWidth: 600})}>
      <View style={[a.gap_lg]}>
        <Text style={[a.text_xl, a.font_bold]}>
          <Trans>Add themes</Trans>
        </Text>
        <Text style={t.atoms.text_contrast_medium}>
          <Trans>
            Enter an account’s handle to browse their themes. Add a theme to
            copy it and all its variants to your collection.
          </Trans>
        </Text>
        <View style={[a.gap_sm]}>
          <TextField.LabelText>
            <Trans>Account handle</Trans>
          </TextField.LabelText>
          <TextField.Root>
            <TextField.Input
              label={l`Account handle`}
              placeholder="alice.bsky.social"
              value={handle}
              onChangeText={setHandle}
              autoCapitalize="none"
              autoCorrect={false}
              autoComplete="off"
              returnKeyType="search"
              onSubmitEditing={submit}
              testID="addThemesHandle"
            />
          </TextField.Root>
          <Button
            label={l`Find themes`}
            color="primary"
            size="large"
            disabled={!valid || query.isFetching}
            onPress={submit}
            testID="findSharedThemes">
            <ButtonText>
              <Trans>Find themes</Trans>
            </ButtonText>
          </Button>
          {!!submitted && (
            <View style={[a.gap_sm]}>
              <Text emoji style={[a.text_md, a.font_semi_bold]}>
                <Trans>Themes from @{submitted}</Trans>
              </Text>
              {query.isFetching && (
                <View style={[a.flex_row, a.align_center, a.gap_sm]}>
                  <Loader size="sm" />
                  <Text accessibilityLiveRegion="polite">
                    <Trans>Loading themes…</Trans>
                  </Text>
                </View>
              )}
              {query.isError && (
                <Text
                  accessibilityRole="alert"
                  style={t.atoms.text_contrast_medium}>
                  <Trans>
                    Couldn’t load this account’s themes. Check the handle and
                    try again.
                  </Trans>
                </Text>
              )}
              {query.isSuccess && !query.isFetching && !themes.length && (
                <Text>
                  <Trans>This account has no themes available to add.</Trans>
                </Text>
              )}
              {themes.map(theme => (
                <SharedThemeRow key={theme.uri} theme={theme} />
              ))}
            </View>
          )}
          {IS_NATIVE && (
            <Button
              label={l`Done`}
              color="secondary"
              size="large"
              onPress={() => control.close()}>
              <ButtonText>
                <Trans>Done</Trans>
              </ButtonText>
            </Button>
          )}
        </View>
      </View>
      <Dialog.Close />
    </Dialog.ScrollableInner>
  )
}

function SharedThemeRow({theme}: {theme: SharedTheme}) {
  const {t: l} = useLingui()
  const t = useTheme()
  const {currentAccount} = useSession()
  const mutation = useAddThemeMutation()
  const [previewing, setPreviewing] = useState(false)
  const group = groupThemes(theme.options, null)[0]
  const name = group.name
  const owned =
    !!currentAccount &&
    theme.uri.startsWith(`at://${currentAccount.did}/${THEME_COLLECTION}/`)
  const added = owned || mutation.isSuccess
  return (
    <View style={[a.gap_xs]}>
      <View
        style={[
          a.flex_row,
          a.align_center,
          a.gap_md,
          a.py_sm,
          {minHeight: 64},
        ]}>
        <ThemePreview group={group} />
        <Button
          label={l`Preview theme ${name}`}
          color="secondary"
          size="small"
          shape="round"
          onPress={() => setPreviewing(true)}
          testID={`previewSharedTheme-${theme.uri}`}>
          <ButtonIcon icon={EyeIcon} />
        </Button>
        <Button
          label={added ? l`Theme ${name} added` : l`Add theme ${name}`}
          color={added ? 'primary_subtle' : 'secondary'}
          size="small"
          shape="round"
          disabled={added || mutation.isPending}
          onPress={() => mutation.mutate(theme)}
          testID={`addSharedTheme-${theme.uri}`}>
          {mutation.isPending ? (
            <Loader size="sm" />
          ) : (
            <ButtonIcon icon={added ? CheckIcon : PlusIcon} />
          )}
        </Button>
      </View>
      {previewing && (
        <ThemePreviewModal
          options={theme.options}
          onClose={() => setPreviewing(false)}
        />
      )}
      {added && (
        <Text
          accessibilityLiveRegion="polite"
          style={t.atoms.text_contrast_medium}>
          <Trans>Added to your themes</Trans>
        </Text>
      )}
      {mutation.isError && (
        <Text accessibilityRole="alert" style={t.atoms.text_contrast_medium}>
          <Trans>
            Couldn’t add this theme. Press the plus button to try again.
          </Trans>
        </Text>
      )}
    </View>
  )
}
