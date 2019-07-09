import {createStore, applyMiddleware} from 'redux';
import createSagaMiddleware from 'redux-saga'
import reducers from './reducers';

const sagaMiddleware = createSagaMiddleware();
const store = createStore(
    reducers,
    applyMiddleware(sagaMiddleware)
);

export default store;
