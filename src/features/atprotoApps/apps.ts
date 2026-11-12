import {type ImageProps} from '#/components/Image'

/**
 * Collection that holds standard.site publications. Every record becomes its
 * own entry, so it is handled separately from `APP_COLLECTIONS`.
 */
export const PUBLICATION_COLLECTION = 'site.standard.publication'

/**
 * Bundled favicon of standard.site, used for publications without their own icon.
 */
export const STANDARD_SITE_FAVICON: ImageProps['source'] = require('../../../assets/icons/community/standard_site_favicon.png')

/**
 * Apps detected by the presence of a collection in a user's repo. Add new
 * entries here.
 */
export const APP_COLLECTIONS: {
  /** Used in test IDs, so keep it a stable slug. */
  id: string
  name: string
  collection: string
  icon: ImageProps['source']
  /** `identifier` is the user's handle, or their DID if the handle is invalid. */
  getUrl: (identifier: string) => string
}[] = [
  {
    id: 'tangled',
    name: 'Tangled',
    collection: 'org.tangled.actor.profile',
    icon: require('../../../assets/icons/community/tangled_favicon.png'),
    getUrl: identifier => `https://tangled.org/${identifier}`,
  },
  {
    id: 'grain',
    name: 'Grain',
    collection: 'social.grain.actor.profile',
    icon: require('../../../assets/icons/community/grain_favicon.png'),
    getUrl: identifier => `https://grain.social/profile/${identifier}`,
  },
  {
    id: 'sifa',
    name: 'sifa.id',
    collection: 'id.sifa.profile.self',
    icon: require('../../../assets/icons/community/sifa_favicon.png'),
    getUrl: identifier => `https://sifa.id/p/${identifier}`,
  },
]
