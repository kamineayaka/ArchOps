import React from 'react';
import ReactDOM from 'react-dom/client';
import { ConfigProvider } from 'antd';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import { DemoUserProvider } from './auth/DemoUserContext';
import './index.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ConfigProvider
      theme={{
        token: {
          colorPrimary: '#2f6b4f',
          borderRadius: 6,
          fontFamily:
            '"IBM Plex Sans", "Noto Sans SC", "Segoe UI", "PingFang SC", sans-serif',
        },
      }}
    >
      <BrowserRouter>
        <DemoUserProvider>
          <App />
        </DemoUserProvider>
      </BrowserRouter>
    </ConfigProvider>
  </React.StrictMode>,
);
