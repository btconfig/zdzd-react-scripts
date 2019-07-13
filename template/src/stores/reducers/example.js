import {fromJS} from 'immutable';
import {SET_COUNT_SYNC} from '../actions/example'

// const initState = fromJS({});
const initState = {
    count:0
};

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
export default reducers;
