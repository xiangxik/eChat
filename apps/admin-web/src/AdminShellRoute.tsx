import { App } from './App';
import { RequireAdmin } from './RequireAdmin';

export function AdminShellRoute() {
  return (
    <RequireAdmin>
      <App />
    </RequireAdmin>
  );
}
