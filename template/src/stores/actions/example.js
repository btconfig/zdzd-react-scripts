import { put } from 'redux-saga/effects'

export function* incrementAsync() {
    yield put({ type: 'INCREMENT' })
}