import {View} from 'react-native'
import {Trans, useLingui} from '@lingui/react/macro'

import {cleanError} from '#/lib/strings/errors'
import * as SettingsList from '#/screens/Settings/components/SettingsList'
import {atoms as a, useTheme} from '#/alf'
import * as Admonition from '#/components/Admonition'
import {Button, ButtonText} from '#/components/Button'
import * as Toggle from '#/components/forms/Toggle'
import {Bookmark_Stroke2_Corner0_Rounded as BookmarkIcon} from '#/components/icons/Bookmark'
import * as toast from '#/components/Toast'
import {Text} from '#/components/Typography'
import {useLocalBookmarks} from './hooks'

export function SearchableBookmarksSettings() {
  const {t: l} = useLingui()
  const t = useTheme()
  const {enabled, setEnabled, status, sync, clear} = useLocalBookmarks()
  const count = status.count
  const storage = (status.bytes / 1048576).toFixed(2)
  return (
    <View>
      <Toggle.Item
        name="searchable-bookmarks"
        testID="searchableBookmarksToggle"
        label={l`Search saved posts`}
        value={enabled}
        onChange={setEnabled}>
        <SettingsList.Item style={[a.align_start]}>
          <SettingsList.ItemIcon icon={BookmarkIcon} />
          <View style={[a.flex_1, a.gap_2xs]}>
            <View style={[a.flex_row, a.align_center, a.gap_sm]}>
              <SettingsList.ItemText>
                <Trans>Search saved posts</Trans>
              </SettingsList.ItemText>
              <Toggle.Platform />
            </View>
            <Text style={t.atoms.text_contrast_medium}>
              <Trans>
                Download saved posts to search them on this device. New saved
                posts sync when you open Saved.
              </Trans>
            </Text>
          </View>
        </SettingsList.Item>
      </Toggle.Item>
      {enabled && (
        <SettingsList.Item
          iconInset
          style={[a.flex_col, a.align_stretch, a.pt_0, a.pb_lg, a.gap_sm]}>
          <Admonition.Outer type="tip">
            <Admonition.Row>
              <Admonition.Icon />
              <Admonition.Content>
                <Text accessibilityLiveRegion="polite">
                  {status.syncing ? (
                    <Trans>Syncing saved posts… {count} downloaded</Trans>
                  ) : status.complete ? (
                    <Trans>{count} saved posts stored</Trans>
                  ) : (
                    <Trans>
                      {count} downloaded · Initial download incomplete
                    </Trans>
                  )}
                </Text>
                <Text style={t.atoms.text_contrast_medium}>
                  <Trans>Compressed storage: {storage} MB</Trans>
                </Text>
                {!!status.error && (
                  <Text style={t.atoms.text_contrast_medium}>
                    <Trans>
                      Could not finish syncing. Your downloaded posts are still
                      available. Retry to continue.
                    </Trans>{' '}
                    {cleanError(status.error)}
                  </Text>
                )}
                <View style={[a.flex_row, a.flex_wrap, a.gap_sm]}>
                  <Button
                    label={l`Sync saved posts`}
                    color="secondary"
                    size="small"
                    disabled={status.syncing}
                    onPress={() => void sync({full: true})}
                    testID="syncSavedPosts">
                    <ButtonText>
                      <Trans>Sync now</Trans>
                    </ButtonText>
                  </Button>
                  <Button
                    label={l`Disable search and clear local saved posts`}
                    color="primary_subtle"
                    size="small"
                    onPress={() => {
                      setEnabled(false)
                      void clear().catch(error => {
                        toast.show(cleanError(error), {type: 'error'})
                      })
                    }}
                    testID="clearLocalSavedPosts">
                    <ButtonText style={{color: t.palette.primary_800}}>
                      <Trans>Disable and clear</Trans>
                    </ButtonText>
                  </Button>
                </View>
              </Admonition.Content>
            </Admonition.Row>
          </Admonition.Outer>
        </SettingsList.Item>
      )}
    </View>
  )
}
