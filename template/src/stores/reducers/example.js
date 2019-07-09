import { fromJS } from 'immutable';

const initState = fromJS({});

function reducers (state = initState, action) {
  switch (action.type) {
    default:
      return state;
  }
}
export default reducers;
