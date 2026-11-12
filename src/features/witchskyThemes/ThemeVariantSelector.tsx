import {ScrollView} from 'react-native'

import {atoms as a} from '#/alf'
import * as SegmentedControl from '#/components/forms/SegmentedControl'
import {type ThemeOption} from './schema'

/** Share spare width between variants; preserve each label's intrinsic width on overflow. */
export function ThemeVariantSelector({
  variants,
  value,
  onChange,
  label,
  disabled = false,
  testIDPrefix,
}: {
  variants: Pick<ThemeOption, 'id' | 'variantName'>[]
  value: string
  onChange: (value: string) => void
  label: string
  disabled?: boolean
  testIDPrefix: string
}) {
  return (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      style={[a.w_full]}
      contentContainerStyle={[a.flex_grow]}>
      <SegmentedControl.Root
        type="radio"
        label={label}
        value={value}
        onChange={onChange}
        style={{width: 'auto', flexBasis: 'auto', flexShrink: 0}}>
        {variants.map(variant => (
          <SegmentedControl.Item
            key={variant.id}
            value={variant.id}
            label={variant.variantName}
            disabled={disabled}
            testID={`${testIDPrefix}${variant.id}`}
            containerStyle={{flexBasis: 'auto', flexShrink: 0}}
            style={{minHeight: 48}}>
            <SegmentedControl.ItemText
              emoji
              numberOfLines={1}
              style={{flexShrink: 0}}>
              {variant.variantName}
            </SegmentedControl.ItemText>
          </SegmentedControl.Item>
        ))}
      </SegmentedControl.Root>
    </ScrollView>
  )
}
