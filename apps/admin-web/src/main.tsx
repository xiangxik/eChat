import { App as AntApp, ConfigProvider, theme } from 'antd';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React, { lazy, Suspense } from 'react';
import ReactDOM from 'react-dom/client';
import { createBrowserRouter, Navigate, RouterProvider } from 'react-router-dom';

import './styles.css';

const AdminShellRoute = lazy(() =>
  import('./AdminShellRoute').then((module) => ({ default: module.AdminShellRoute })),
);
const AuditLogsPage = lazy(() => import('./pages/AuditLogsPage').then((module) => ({ default: module.AuditLogsPage })));
const ChatbotsPage = lazy(() => import('./pages/ChatbotsPage').then((module) => ({ default: module.ChatbotsPage })));
const ContextPoliciesPage = lazy(() =>
  import('./pages/ContextPoliciesPage').then((module) => ({ default: module.ContextPoliciesPage })),
);
const DashboardPage = lazy(() => import('./pages/DashboardPage').then((module) => ({ default: module.DashboardPage })));
const EvalsPage = lazy(() => import('./pages/EvalsPage').then((module) => ({ default: module.EvalsPage })));
const LoginPage = lazy(() => import('./pages/LoginPage').then((module) => ({ default: module.LoginPage })));
const ModelsPage = lazy(() => import('./pages/ModelsPage').then((module) => ({ default: module.ModelsPage })));
const ProvidersPage = lazy(() => import('./pages/ProvidersPage').then((module) => ({ default: module.ProvidersPage })));

function lazyPage(element: React.ReactNode) {
  return <Suspense fallback={null}>{element}</Suspense>;
}

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: false,
    },
  },
});

const router = createBrowserRouter([
  { path: '/login', element: lazyPage(<LoginPage />) },
  {
    path: '/',
    element: lazyPage(<AdminShellRoute />),
    children: [
      { index: true, element: <Navigate to="/dashboard" replace /> },
      { path: 'dashboard', element: lazyPage(<DashboardPage />) },
      { path: 'providers', element: lazyPage(<ProvidersPage />) },
      { path: 'models', element: lazyPage(<ModelsPage />) },
      { path: 'chatbots', element: lazyPage(<ChatbotsPage />) },
      { path: 'context-policies', element: lazyPage(<ContextPoliciesPage />) },
      { path: 'evals', element: lazyPage(<EvalsPage />) },
      { path: 'audit-logs', element: lazyPage(<AuditLogsPage />) },
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
