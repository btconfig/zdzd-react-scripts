import {put, call, takeLatest, takeEvery} from 'redux-saga/effects';

// 同步action
const SET_COUNT_SYNC = Symbol('set_count')
const SET_COUNT = Symbol('set_count')

// 异步action
function* getNewCount({payload, resolve, reject}) {
    const res = yield call(/*异步请求*/)
    const tempResult = 20
    //异步请求结束后，可以发送同步action到reducer
    yield put({
        type: SET_COUNT_SYNC,
        payload:{
            count:tempResult
        }
    });
    // 根据返回值，判断resolve或者reject
    // resolve()
    // reject()
}

// 默认导出需要监听的action（type字段），及监听到后的处理函数（字段asyncFunc）
export default [
    {
        effect:takeLatest,  //?????
        type:SET_COUNT,
        asyncFunc:getNewCount
    }
]

export {SET_COUNT_SYNC, SET_COUNT}