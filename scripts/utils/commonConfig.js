'use strict';

const fs = require('fs-extra');

const getHuskyConfig = () => {
  return {
    hooks: {
      'pre-commit': 'lint-staged',
    },
  };
};
const getLintStagedConfig = () => {
  return {
    '*.{js,mjs,jsx,ts,tsx,md,css,html}': [
      'prettier --trailing-comma es5 --single-quote --write',
      'git add',
    ],
    '*.{js,jsx}': ['eslint --fix', 'git add'],
  };
};
const copyEslintConfig = (src, dest) => {
  const eslintrcExists = fs.existsSync(dest);
  if (eslintrcExists) {
    fs.removeSync(dest);
  }
  fs.copySync(src, dest);
};
const getScriptsConfig = () => {
  return {
    start: 'dzd-react-scripts start',
    build: 'dzd-react-scripts build',
    test: 'dzd-react-scripts test',
    update: 'dzd-react-scipts update',
    eject: 'dzd-react-scripts eject',
  };
};
const getPackageConfig = () => {
  return {
    husky: getHuskyConfig(),
    'lint-staged': getLintStagedConfig(),
    scripts: getScriptsConfig(),
  };
};

module.exports = {
  getPackageConfig,
  copyEslintConfig,
};
