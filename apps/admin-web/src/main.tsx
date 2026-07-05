import { App as AntApp, ConfigProvider, theme } from 'antd';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import ReactDOM from 'react-dom/client';
import { createBrowserRouter, Navigate, RouterProvider } from 'react-router-dom';

import { App } from './App';
import { RequireAdmin } from './RequireAdmin';
import { ChatbotsPage } from './pages/ChatbotsPage';
import { ContextPoliciesPage } from './pages/ContextPoliciesPage';
import { DashboardPage } from './pages/DashboardPage';
import { EvalsPage } from './pages/EvalsPage';
import { LoginPage } from './pages/LoginPage';
import { ModelsPage } from './pages/ModelsPage';
import { ProvidersPage } from './pages/ProvidersPage';
import './styles.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: false,
    },
  },
});

const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    path: '/',
    element: (
      <RequireAdmin>
        <App />
      </RequireAdmin>
    ),
    children: [
      { index: true, element: <Navigate to="/dashboard" replace /> },
      { path: 'dashboard', element: <DashboardPage /> },
      { path: 'providers', element: <ProvidersPage /> },
      { path: 'models', element: <ModelsPage /> },
      { path: 'chatbots', element: <ChatbotsPage /> },
      { path: 'context-policies', element: <ContextPoliciesPage /> },
      { path: 'evals', element: <EvalsPage /> },
    ],
  },
]);

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ConfigProvider
      theme={{
        algorithm: theme.defaultAlgorithm,
        token: {
          colorPrimary: '#1f7a68',
          borderRadius: 6,
          fontFamily: 'Aptos, Segoe UI, sans-serif',
        },
      }}
    >
      <AntApp>
        <QueryClientProvider client={queryClient}>
          <RouterProvider router={router} />
        </QueryClientProvider>
      </AntApp>
    </ConfigProvider>
  </React.StrictMode>,
);
