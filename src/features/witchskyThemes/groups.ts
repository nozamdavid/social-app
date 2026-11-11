import {type ThemeOption} from '#/features/witchskyThemes/schema'

export type ThemeGroup = {
  uri: string
  name: string
  variants: ThemeOption[]
  cachedVariantId?: string
}

/** Group by record identity, preserving cached selections when a fetch omits them. */
export function groupThemes(
  options: ThemeOption[],
  selected: ThemeOption | null,
): ThemeGroup[] {
  const groups = new Map<string, ThemeGroup>()
  for (const option of options) {
    let group = groups.get(option.uri)
    if (!group) {
      group = {uri: option.uri, name: option.name, variants: []}
      groups.set(option.uri, group)
    }
    const existing = group.variants.findIndex(
      variant => variant.id === option.id,
    )
    if (existing === -1) group.variants.push(option)
    else group.variants[existing] = option
  }
  if (selected) {
    const group = groups.get(selected.uri)
    if (!group) {
      groups.set(selected.uri, {
        uri: selected.uri,
        name: selected.name,
        variants: [selected],
        cachedVariantId: selected.id,
      })
    } else if (!group.variants.some(variant => variant.id === selected.id)) {
      group.variants.push(selected)
      group.cachedVariantId = selected.id
    }
  }
  return [...groups.values()]
}

/** An active variant remains selected when its parent theme row is tapped. */
export function getThemeGroupOption(
  group: ThemeGroup,
  selectedId: string,
): ThemeOption {
  return (
    group.variants.find(variant => variant.id === selectedId) ??
    group.variants[0]
  )
}
