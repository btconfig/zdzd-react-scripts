import React from 'react';
import { Provider } from 'react-redux';
import { HashRouter as Router, Route } from 'react-router-dom';
import './App.css';
import store from './stores';
import Index from './pages';
import Example from './pages/Example'

function App () {
  return (
    <Provider store={store}>
      <Router>
        <Route path="/" component={Index}></Route>
        <Route path="/example" component={Example}></Route>
      </Router>
    </Provider>
  );
}

export default App;
