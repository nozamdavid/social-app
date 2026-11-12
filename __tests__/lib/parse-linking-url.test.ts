import {parseLinkingUrl} from '#/lib/parseLinkingUrl'

it.each(['bluesky', 'blueskyplus'])('normalizes %s compose links', scheme => {
  const url = parseLinkingUrl(`${scheme}://intent/compose?text=hello`)
  expect(url.host).toBe('')
  expect(url.pathname).toBe('/intent/compose')
  expect(url.searchParams.get('text')).toBe('hello')
  expect(parseLinkingUrl(`${scheme}:///settings`).pathname).toBe('/settings')
})

it('preserves canonical web links', () => {
  const url = 'https://bsky.app/profile/alice.test'
  expect(parseLinkingUrl(url).toString()).toBe(url)
})
