'use strict';

const onProxyRes = proxyRes => {
  const cookies = proxyRes.headers['set-cookie'];
  const cookieRegex = /Domain=/i;
  if (cookies) {
    const newCookies = cookies.map(cookie => {
      if (cookieRegex.test(cookie)) {
        return cookie
          .split(';')
          .map(item => {
            if (item.test(cookieRegex)) {
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
  return { onProxyRes, ...proxyConfig };
};

module.exports = { addProxyRes };
