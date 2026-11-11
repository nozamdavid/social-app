/**
 * Apply app identity to the exported HTML without renaming network services.
 * @param {string} html
 * @param {{name: string, colors: {dark: string}}} brand
 * @param {string} mark
 * @returns {string}
 */
module.exports = function brandWebHtml(html, brand, mark) {
  const name = brand.name.replace(
    /[&<>"']/g,
    char =>
      ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;',
      })[char] ?? char,
  )
  return html
    .replace(
      /(<div id="splash">\s*)(?:<!--[\s\S]*?-->\s*)?<svg[\s\S]*?<\/svg>/,
      (_, before) => before + mark,
    )
    .replace('<h1 lang="en">Bluesky</h1>', `<h1 lang="en">${name}</h1>`)
    .replace(
      'Enable JavaScript to use Bluesky.',
      `Enable JavaScript to use ${name}.`,
    )
    .replaceAll('#151D28', brand.colors.dark)
}
