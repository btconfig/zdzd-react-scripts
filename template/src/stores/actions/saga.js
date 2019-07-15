import Example from './example';

const arrList = [...Example];

function* rootSaga () {
  for (const item of arrList) {
    yield item.effect(item.type, item.asyncFunc);
  }
}

export default rootSaga;
