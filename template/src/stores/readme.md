# 状态管理

## 代码流程

1. 首先，在UI组件中通过dispatch的方式发送action，有些action直接通过reducer改变store（我们称为同步action），但有些action需要通过异步请求的返回值设定reducer再去改变store（我们称为异步action）。
为了区分这两种action，我们建议：在同步action的type中，加后缀“_SYNC”作为区分；或者异步action的type加后缀“_ASYNC”作为区分。
```
// src/pages/Example.js
// 同步action：
this.props.dispatch({
   type:SET_COUNT_SYNC,
   payload: {count:0}
})
// 异步action：
this.props.dispatch({
   type:SET_COUNT,
   payload: {
       //异步请求的一些参数
   },
})
```
对于异步action，我们可能需要获取到异步请求的返回结果，需手动添加promise
```
new Promise((resolve, reject) => {
   this.props.dispatch({
      type:SET_COUNT,
      payload: {
         /异步请求的一些参数
      },
      resolve,
      reject
   }).then(res => {
      // 成功的回调
   }).catch(err => {
       // 失败的回调
   })
})
```

2、对于同步action（例子中为SET_COUNT_SYNC），我们希望在发送action后立即调用reducer来改变state。
```
// src/stores/reducers/example.js
function reducers(state = initState, action) {
    const {type, payload}=action
    switch (type) {
        case SET_COUNT_SYNC:
            return {...state, ...payload}
            break
        default:
            return state;
    }
}
```

3、对于异步action（例子中为SET_COUNT），我们希望在异步请求结束后再调用reducer（而非在发送action后立即调用reducer），所以reducer中不应该有此action。
   同时应当将该action与期望执行的异步函数通过redux-saga进行绑定，这样saga监听到该action后，就会调用该异步函数。
```
// src/stores/actions/example.js
// action SET_COUNT绑定的异步请求
function* getNewCount({payload, resolve, reject}) {
    const res = yield call(/*异步请求*/)
    //异步请求结束后，可以发送同步action到reducer
    yield put({
        type: SET_COUNT_SYNC,
        payload:{
            count:res
        }
    });
    // 根据返回值，判断resolve或者reject
    // resolve()
    // reject()
}

// 将SET_COUNT的action与getNewCount异步函数进行绑定
// 默认导出需要监听的action类型（即type字段），及监听到后的处理函数（字段asyncFunc）
// 在actions/saga.js中实现绑定，真正的监听在stores/index.js中实现
export default [
    {
        effect:takeLatest,
        type:SET_COUNT,
        asyncFunc:getNewCount
    }
]

// src/stores/actions/saga.js
import Example from './example'
const arrList = [
    ...Example
]
function* rootSaga() {
    for (const item of arrList) {
        yield item.effect(item.type, item.asyncFunc)
    }
}

// src/stores/index.js
import { createStore, applyMiddleware } from 'redux';
import createSagaMiddleware from 'redux-saga';
import reducers from './reducers';
import rootSaga from './actions/saga'

const sagaMiddleware = createSagaMiddleware();
const store = createStore(reducers, applyMiddleware(sagaMiddleware));
sagaMiddleware.run(rootSaga)
```