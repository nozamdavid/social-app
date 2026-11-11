import {rewriteImageUrl} from './config'

/** Preserve local assets, image refs, metadata and identity when no rewrite is needed. */
export function rewriteImageSource<T>(source: T, hostname: string): T {
  if (!hostname || !source) return source
  if (typeof source === 'string') return rewriteImageUrl(source, hostname) as T
  if (Array.isArray(source))
    return source.map(item => rewriteImageSource(item, hostname)) as T
  if (
    typeof source === 'object' &&
    'uri' in source &&
    typeof source.uri === 'string'
  ) {
    const uri = rewriteImageUrl(source.uri, hostname)
    if (uri !== source.uri) return {...source, uri}
  }
  return source
}
