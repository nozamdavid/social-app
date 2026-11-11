import {ScrollView, View} from 'react-native'
import {useSharedValue} from 'react-native-reanimated'
import {moderatePost} from '@bsky/sdk/moderation'
import {useLingui} from '@lingui/react/macro'

import {BOTTOM_BAR_AVI, DEMO_FEED} from '#/lib/demo'
import {useModerationOpts} from '#/state/preferences/moderation-opts'
import {TabBar} from '#/view/com/pager/TabBar'
import {PostFeedItem} from '#/view/com/posts/PostFeedItem'
import {UserAvatar} from '#/view/com/util/UserAvatar'
import {Logo} from '#/view/icons/Logo'
import {styles as bottomBarStyles} from '#/view/shell/bottom-bar/BottomBarStyles'
import {atoms as a, useTheme} from '#/alf'
import {Bell_Stroke2_Corner0_Rounded as BellIcon} from '#/components/icons/Bell'
import {Hashtag_Stroke2_Corner0_Rounded as FeedsIcon} from '#/components/icons/Hashtag'
import {HomeOpen_Filled_Corner0_Rounded as HomeIcon} from '#/components/icons/HomeOpen'
import {MagnifyingGlass_Stroke2_Corner0_Rounded as SearchIcon} from '#/components/icons/MagnifyingGlass'
import {Message_Stroke2_Corner0_Rounded as MessageIcon} from '#/components/icons/Message'
import {Pencil_Stroke2_Corner0_Rounded as PencilIcon} from '#/components/icons/Pencil'
import * as Layout from '#/components/Layout'
import {ScrollbarOffsetContext} from '#/components/Layout/context'
import {app} from '#/lexicons'
import * as bsky from '#/types/bsky'

/** Reuse the actual feed rows, header and tabs; sample controls cannot perform actions. */
export function ThemeFeedPreview() {
  const t = useTheme()
  const {t: l} = useLingui()
  const dragProgress = useSharedValue(0)
  const dragState = useSharedValue<'idle' | 'dragging' | 'settling'>('idle')
  return (
    <ScrollbarOffsetContext.Provider value={{isWithinOffsetView: true}}>
      <View testID="themePreviewFeed" style={[a.flex_1, t.atoms.bg]}>
        <View
          pointerEvents="none"
          accessibilityElementsHidden
          importantForAccessibility="no-hide-descendants">
          <Layout.Header.Outer noBottomBorder sticky={false}>
            <Layout.Header.MenuButton />
            <View style={[a.flex_1, a.align_center]}>
              <Logo width={30} />
            </View>
            <Layout.Header.Slot>
              <View style={[a.align_end]}>
                <FeedsIcon size="lg" fill={t.atoms.text.color} />
              </View>
            </Layout.Header.Slot>
          </Layout.Header.Outer>
          <TabBar
            testID="themePreviewTabs"
            selectedPage={0}
            items={[l`Following`, l`Discover`]}
            dragProgress={dragProgress}
            dragState={dragState}
            transparent
          />
        </View>
        <View style={[a.flex_1]}>
          <ScrollView nestedScrollEnabled style={[a.flex_1]}>
            <View
              pointerEvents="none"
              accessibilityElementsHidden
              importantForAccessibility="no-hide-descendants">
              {[DEMO_FEED.feed[2], DEMO_FEED.feed[1], DEMO_FEED.feed[0]].map(
                ({post}, index) => (
                  <PreviewPost key={post.uri} post={post} first={index === 0} />
                ),
              )}
            </View>
          </ScrollView>
          <View
            pointerEvents="none"
            accessibilityElementsHidden
            importantForAccessibility="no-hide-descendants"
            style={[
              a.absolute,
              a.align_center,
              a.justify_center,
              a.rounded_full,
              {
                width: 56,
                height: 56,
                bottom: 16,
                right: 24,
                backgroundColor: t.palette.primary_500,
              },
            ]}>
            <PencilIcon size="lg" fill={t.onAccent ?? t.palette.white} />
          </View>
        </View>
        <PreviewBottomBar />
      </View>
    </ScrollbarOffsetContext.Provider>
  )
}

function PreviewPost({
  post,
  first,
}: {
  post: app.bsky.feed.defs.PostView
  first: boolean
}) {
  const moderationOpts = useModerationOpts()
  if (!moderationOpts || !bsky.matches(app.bsky.feed.post, post.record))
    return null
  return (
    <PostFeedItem
      post={post}
      rootPost={post}
      record={post.record}
      moderation={moderatePost(post, moderationOpts)}
      hideTopBorder={first}
      reason={undefined}
      parentAuthor={undefined}
      showReplyTo={false}
      feedContext={undefined}
      reqId={undefined}
    />
  )
}

function PreviewBottomBar() {
  const t = useTheme()
  const icons = [HomeIcon, SearchIcon, MessageIcon, BellIcon]
  return (
    <View
      pointerEvents="none"
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants"
      style={[
        bottomBarStyles.bottomBar,
        t.atoms.bg,
        t.atoms.border_contrast_low,
        {position: 'relative', paddingBottom: 15},
      ]}>
      {icons.map((Icon, index) => (
        <View key={index} style={bottomBarStyles.ctrl}>
          <Icon
            width={index === 1 ? 30 : 28}
            style={[bottomBarStyles.ctrlIcon, t.atoms.text]}
          />
        </View>
      ))}
      <View style={bottomBarStyles.ctrl}>
        <View style={bottomBarStyles.ctrlIcon}>
          <UserAvatar
            avatar={BOTTOM_BAR_AVI}
            size={26}
            type="user"
            usePlainRNImage
            hideLiveBadge
          />
        </View>
      </View>
    </View>
  )
}
