import { LockOutlined, MessageOutlined } from '@ant-design/icons';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Alert, Button, Card, Form, Input, Typography } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';

import { loginAdmin } from '../api/client';

const { Text, Title } = Typography;

interface LoginValues {
  username?: string;
  password: string;
}

interface LocationState {
  from?: { pathname?: string };
}

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const state = location.state as LocationState | null;
  const redirectTo = state?.from?.pathname && state.from.pathname !== '/login' ? state.from.pathname : '/dashboard';

  const loginMutation = useMutation({
    mutationFn: (values: LoginValues) => loginAdmin(values.password, values.username),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['admin-session'] });
      navigate(redirectTo, { replace: true });
    },
  });

  return (
    <main className="login-shell">
      <Card className="login-card">
        <div className="login-brand">
          <MessageOutlined />
          <span>eChat Admin</span>
        </div>
        <Title level={2}>Admin Login</Title>
        <Text type="secondary">Sign in to manage providers, models, chatbots, and context policies.</Text>
        <Form className="login-form" layout="vertical" requiredMark={false} onFinish={(values) => loginMutation.mutate(values)}>
          <Form.Item label="Username" name="username" rules={[{ max: 128 }]}>
            <Input autoComplete="username" />
          </Form.Item>
          <Form.Item
            label="Admin password"
            name="password"
            rules={[{ required: true, message: 'Enter the admin password' }]}
          >
            <Input.Password prefix={<LockOutlined />} autoFocus autoComplete="current-password" />
          </Form.Item>
          {loginMutation.isError && (
            <Alert
              type="error"
              showIcon
              message={loginMutation.error instanceof Error ? loginMutation.error.message : 'Login failed'}
            />
          )}
          <Button type="primary" htmlType="submit" block loading={loginMutation.isPending}>
            Sign in
          </Button>
        </Form>
      </Card>
    </main>
  );
}