import React from 'react'
import connect from 'redux-saga'
import {SET_COUNT_SYNC,SET_COUNT} from '../stores/actions/example'

class Example extends React.Component {
    constructor(props) {
        super(props)
        this.state = {}
    }

    handleSync = () => {
        this.props.dispatch({
            type:SET_COUNT_SYNC,
            payload: {count:parseInt(Math.random()*1000)}
        })
    }

    handleAsync = () => {
        //如果需要获取到异步请求的返回结果，需手动添加promise
        new Promise((resolve, reject) => {
            this.props.dispatch({
                type:SET_COUNT,
                // dispatch一个同步action，会直接去对应的reducer中操作state（所以reducer中不应该有此action）；
                // 同时在saga中监听，当该action有对应的异步执行函数，会去执行该函数。
                // 建议：同步action的type中，加后缀“_SYNC”作为区分；或者异步action的type加后缀“_ASYNC”作为区分。
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