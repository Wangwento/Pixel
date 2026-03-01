import { createBrowserRouter, Navigate } from 'react-router-dom';
import AppLayout from '../components/Layout';
import Home from '../pages/Home';
import Generate from '../pages/Generate';
import History from '../pages/History';
import Login from '../pages/Login';

// 路由守卫组件
const ProtectedRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const token = localStorage.getItem('token');
  if (!token) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
};

const router = createBrowserRouter([
  {
    path: '/',
    element: <Home />,
  },
  {
    path: '/login',
    element: <Login />,
  },
  {
    path: '/',
    element: <AppLayout />,
    children: [
      {
        path: 'generate',
        element: <Generate />,
      },
      {
        path: 'history',
        element: (
          <ProtectedRoute>
            <History />
          </ProtectedRoute>
        ),
      },
    ],
  },
]);

export default router;
