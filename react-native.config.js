module.exports = {
  dependencies:
    process.env.EXPO_PUBLIC_APP_VARIANT === 'bluesky-plus'
      ? {'@bitdrift/react-native': {platforms: {android: null}}}
      : {},
}
