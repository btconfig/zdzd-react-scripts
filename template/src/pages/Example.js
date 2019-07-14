import React from 'react'
import connect from 'redux-saga'
import {SET_COUNT_SYNC,SET_COUNT} from '../stores/actions/example'

class Example extends React.Component {
    constructor(props) {
        super(props)
        this.state = {}
    }

    handleSync = () => {
        /*
         1、dispatch一个同步action，会直接去对应的reducer中操作state（我们希望该同步action立即调用reducer，
            所以需要在reducer中添加此action）；
         2、同时为了方便进行action类型的管理，我们在每个模块的action文件中定义了该模块的所有action类型。UI组件、
            相应模块reducer都需要引入定义好的action类型进行相应的操作或判断。
         3、建议：同步action的type中，加后缀“_SYNC”作为区分；或者异步action的type加后缀“_ASYNC”作为区分。
        */
        this.props.dispatch({
            type:SET_COUNT_SYNC,
            payload: {count:parseInt(Math.random()*1000)}
        })
    }

    handleAsync = () => {
        //如果需要获取到异步请求的返回结果，需手动添加promise
        new Promise((resolve, reject) => {
            /*
             1、dispatch一个同步action，会直接去对应的reducer中操作state（我们希望在异步请求结束后再调用reducer，
                所以reducer中不应该有此action）；同时在saga.js中监听，当该action有对应的异步执行函数，会去执行该函数。
             2、建议：同步action的type中，加后缀“_SYNC”作为区分；或者异步action的type加后缀“_ASYNC”作为区分。
            */
            this.props.dispatch({
                type:SET_COUNT,
                payload: {
                    //异步请求的一些参数
                },
                resolve,
                reject
            }).then(res => {
                // 成功的回调
            }).catch(err => {
                // 失败的回调
            })
        })
    }

    render() {
        const {count}=this.props
        return (
            <div>
                <div style={{border: '1px solid'}}>
                    <h4>同步action：</h4>
                    <span>count:{count}</span>
                    <button onClick={this.handleSync}>点我</button>
                </div>
                <div style={{border: '1px solid red'}}>
                    <h4>异步action举例：从服务端获取新的值</h4>
                    <span>count:{count}</span>
                    <button onClick={this.handleAsync}>点我</button>
                </div>
            </div>
        )
    }
}

function mapStateToProps(state) {
    return {
        count: state.example.count
    }
}

export default connect(mapStateToProps)(Example)