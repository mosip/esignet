const webpack = require('webpack');

module.exports = {
  webpack: {
    configure: (webpackConfig) => {
      // 1. Fix the "Fully Specified" ESM error (The Axios error)
      webpackConfig.module.rules.push({
        test: /\.m?js/,
        resolve: {
          fullySpecified: false,
        },
      });

      // 2. Add the Fallbacks (The Crypto/Process error)
      webpackConfig.resolve.fallback = {
        ...webpackConfig.resolve.fallback,
        "crypto": require.resolve("crypto-browserify"),
        "stream": require.resolve("stream-browserify"),
        "vm": require.resolve("vm-browserify"),
        "buffer": require.resolve("buffer"),
        "process": require.resolve("process/browser.js"), // Added .js here
      };

      // 3. Global Plugins
      webpackConfig.plugins.push(
        new webpack.ProvidePlugin({
          Buffer: ['buffer', 'Buffer'],
          process: 'process/browser.js', // Added .js here
        })
      );

      return webpackConfig;
    },
  },
};