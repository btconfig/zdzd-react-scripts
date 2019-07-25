'use strict';

// Makes the script crash on unhandled rejections instead of silently
// ignoring them. In the future, promise rejections that are not handled will
// terminate the Node.js process with a non-zero exit code.
process.on('unhandledRejection', (err) => {
  throw err;
});

const path = require('path');
const fs = require('fs-extra');
const os = require('os');
const paths = require('../config/paths');
const cc = require('./utils/commonConfig');

const appPackage = require(paths.appPackageJson);

// Setup the app package
Object.assign(appPackage, cc.getPackageConfig());
fs.writeFileSync(
  paths.appPackageJson,
  JSON.stringify(appPackage, null, 2) + os.EOL
);

// Copy the eslint configuration
const eslintrcSrcPath = path.join(paths.ownPath, 'config/eslint-config.json');
const eslintrcDestPath = path.join(paths.appPath, '.eslintrc');
cc.copyEslintConfig(eslintrcSrcPath, eslintrcDestPath);
