import { put, call, takeLatest } from 'redux-saga/effects';

// action类型
const SET_COUNT_SYNC = Symbol('set_count');
const SET_COUNT = Symbol('set_count');

// 某些action绑定的异步请求
function* getNewCount ({ payload, resolve, reject }) {
  const res = yield call(/* 异步请求*/);
  // 异步请求结束后，可以发送同步action到reducer
  yield put({
    type: SET_COUNT_SYNC,
    payload: {
      count: res,
    },
  });
  // 根据返回值，判断resolve或者reject
  // resolve()
  // reject()
}

// 默认导出需要监听的action类型（即type字段），及监听到后的处理函数（字段asyncFunc）
// 真正的监听在stores/index.js中实现
export default [
  {
    effect: takeLatest, // ?????
    type: SET_COUNT,
    asyncFunc: getNewCount,
  },
];

export { SET_COUNT_SYNC, SET_COUNT };
