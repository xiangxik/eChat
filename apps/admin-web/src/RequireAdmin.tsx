import { LoadingOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';

import { fetchAdminSession } from './api/client';

interface RequireAdminProps {
  children: ReactNode;
}

export function RequireAdmin({ children }: RequireAdminProps) {
  const location = useLocation();
  const sessionQuery = useQuery({
    queryKey: ['admin-session'],
    queryFn: fetchAdminSession,
    retry: false,
  });

  if (sessionQuery.isLoading) {
    return (
      <div className="auth-loading">
        <LoadingOutlined />
      </div>
    );
  }

  if (sessionQuery.isError || !sessionQuery.data?.authenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  return children;
}