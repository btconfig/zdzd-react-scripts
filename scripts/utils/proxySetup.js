'use strict';

require('./polyfills.js');

const onProxyRes = proxyRes => {
  const cookies = proxyRes.headers['set-cookie'];
  const cookieRegex = /Domain=/i;
  if (cookies) {
    const newCookies = cookies.map(cookie => {
      if (cookieRegex.test(cookie)) {
        return cookie
          .split(';')
          .map(item => {
            if (cookieRegex.test(item)) {
              return 'Domain=localhost';
            }
            return item;
          })
          .join(';');
      }
      return cookie;
    });
    Reflect.deleteProperty(proxyRes.headers, 'set-cookie');
    proxyRes.headers['set-cookie'] = newCookies;
  }
};

const addProxyRes = proxyConfig => {
  const type = Object.prototype.toString.call(proxyConfig);
  switch (type) {
    case '[object Object]': {
      const configPairs = Object.entries(proxyConfig).map(item => {
        const [key, value] = item;
        if (Object.prototype.toString.call(value) === '[object Object]') {
          return [key, { onProxyRes, ...value }];
        }
        return item;
      });
      return Object.fromEntries(configPairs);
    }
    case '[object Array]':
      return proxyConfig.map(config => {
        return { onProxyRes, ...config };
      });
    default:
      return proxyConfig;
  }
};

module.exports = { addProxyRes };
