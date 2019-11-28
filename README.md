# @zdzd/react-scripts

代码库 fork 自https://github.com/facebook/create-react-app.git

代码库包含[Create React App](https://github.com/facebook/create-react-app)使用到的脚本和配置。<br>
下面是 Create React App 的文档:

- [开始指南](https://facebook.github.io/create-react-app/docs/getting-started) – 如何创建一个 react 应用。
- [用户指南](https://facebook.github.io/create-react-app/) – 如何用 Create React App 来开发一个应用。

## 快速构建

```
npm install create-react-app
create-react-app xxx --scripts-version @zdzd/react-scripts
```

## 特色配置

1. antd 的按需引入
2. lodash 的按需引入
3. eslint 配置
4. husky + lint-stage，对提交代码做格式化和 eslint 检查
5. 可在 package.json 中配置 resolve.alias
6. package.json 的 proxy 字段，支持原生 proxy 配置（参考 webpack-dev-server）

## 集成模块

1. redux、redux-saga
2. react-router，v4
