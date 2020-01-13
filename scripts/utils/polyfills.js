
'use strict';

// polyfill Object.fromEntries before v12.0.0
Object.fromEntries = Object.fromEntries || (pairs => {
  if (pairs === undefined || pairs[Symbol.iterator] === undefined) {
    throw new Error('Parameter \'pairs\' is not iterable!');
  }

  const res = {};
  for (const [key, value] of pairs) {
    res[key] = value;
  }
  return res;
});

