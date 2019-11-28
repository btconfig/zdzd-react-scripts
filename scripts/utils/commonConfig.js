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
    '*.{mjs,ts,tsx,md,css,html}': [
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
const getPackageConfig = () => {
  return {
    husky: getHuskyConfig(),
    'lint-staged': getLintStagedConfig(),
  };
};

module.exports = {
  getPackageConfig,
  copyEslintConfig,
};
