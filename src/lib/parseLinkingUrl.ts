export function parseLinkingUrl(url: string): URL {
  /*
   * Hack: add a third slash to bluesky:// urls so that `URL.host` is empty and
   * `URL.pathname` has the full path.
   */
  for (const scheme of ['bluesky', 'blueskyplus']) {
    if (url.startsWith(`${scheme}://`) && !url.startsWith(`${scheme}:///`)) {
      url = url.replace(`${scheme}://`, `${scheme}:///`)
      break
    }
  }
  return new URL(url)
}
