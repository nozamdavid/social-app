import {tokens} from '@bsky.app/alf'

import brandPalette from '#/../assets/branding/caelum/generated/palette.json'
import {IS_BLUESKY_PLUS} from '#/env'

export * from '@bsky.app/alf/dist/tokens'

export const color = {
  temp_purple: tokens.labelerColor.purple,
  temp_purple_dark: tokens.labelerColor.purple_dark,
} as const

export const gradients = {
  primary: {
    values: IS_BLUESKY_PLUS
      ? [
          [0, brandPalette.defaultPalette.primary_600],
          [1, brandPalette.defaultPalette.primary_400],
        ]
      : [
          [0, '#054CFF'],
          [0.4, '#1085FE'],
          [0.6, '#1085FE'],
          [1, '#59B9FF'],
        ],
    hover_value: IS_BLUESKY_PLUS
      ? brandPalette.defaultPalette.primary_600
      : '#1085FE',
  },
  sky: {
    values: IS_BLUESKY_PLUS
      ? [
          [0, brandPalette.defaultPalette.primary_500],
          [1, brandPalette.defaultPalette.primary_300],
        ]
      : [
          [0, '#0A7AFF'],
          [1, '#59B9FF'],
        ],
    hover_value: IS_BLUESKY_PLUS
      ? brandPalette.defaultPalette.primary_500
      : '#0A7AFF',
  },
  midnight: {
    values: [
      [0, '#022C5E'],
      [1, '#4079BC'],
    ],
    hover_value: '#022C5E',
  },
  sunrise: {
    values: [
      [0, '#4E90AE'],
      [0.4, '#AEA3AB'],
      [0.8, '#E6A98F'],
      [1, '#F3A84C'],
    ],
    hover_value: '#AEA3AB',
  },
  sunset: {
    values: [
      [0, '#6772AF'],
      [0.6, '#B88BB6'],
      [1, '#FFA6AC'],
    ],
    hover_value: '#B88BB6',
  },
  summer: {
    values: [
      [0, '#FF6A56'],
      [0.3, '#FF9156'],
      [1, '#FFDD87'],
    ],
    hover_value: '#FF9156',
  },
  nordic: {
    values: [
      [0, '#083367'],
      [1, '#9EE8C1'],
    ],
    hover_value: '#3A7085',
  },
  bonfire: {
    values: [
      [0, '#203E4E'],
      [0.4, '#755B62'],
      [0.8, '#CD7765'],
      [1, '#EF956E'],
    ],
    hover_value: '#755B62',
  },
} as const
