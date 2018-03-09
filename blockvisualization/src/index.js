import React from 'react';
import {render} from 'react-dom';
import reducer from './reducer';
import {Provider} from 'react-redux';
import {createStore} from 'redux';
import TangleContainer from './containers/TangleContainer';

const store = createStore(reducer);

render(
  <Provider store={store}>
    <div>
      <div className='title-bar-container'>
        <div className='left-title'>
          <h2>Iota Tangle Visualization</h2>
        </div>
        <div className='right-title'>
          <p>
            This demo shows the tangle structure behind Iota, as described in
            the <a href='https://iota.org/IOTA_Whitepaper.pdf'>white paper</a>.
          </p>
          <p>
            The source code can be found on <a href="https://github.com/iotaledger/iotavisualization">github</a>.
          </p>
        </div>
      </div>
      <TangleContainer />
    </div>
  </Provider>,
  document.getElementById('container')
);
