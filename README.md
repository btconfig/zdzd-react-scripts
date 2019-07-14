# @zdzd/react-scripts

代码库fork自https://github.com/facebook/create-react-app.git

代码库包含[Create React App](https://github.com/facebook/create-react-app)使用到的脚本和配置。<br>
下面是Create React App的文档:

- [开始指南](https://facebook.github.io/create-react-app/docs/getting-started) – 如何创建一个react应用。
- [用户指南](https://facebook.github.io/create-react-app/) – 如何用Create React App来开发一个应用。

## 快速构建
```
npm install create-react-app
create-react-app xxx --scripts-version @zdzd/react-scripts
```

## 特色配置
1. antd的按需引入
2. lodash的按需引入
3. eslint配置
4. husky + lint-stage，对提交代码做格式化和eslint检查
5. 可在package.json中配置resolve.alias

## 集成模块
1. redux、redux-saga
2. react-router，v4
