import { createBrowserRouter, Navigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import AppLayout from '../components/Layout';
import Home from '../pages/Home';
import Generate from '../pages/Generate';
import History from '../pages/History';
import Login from '../pages/Login';
import Dashboard from '../pages/Dashboard';
import DashboardGenerate from '../pages/Dashboard/Generate';
import Assets from '../pages/Assets';
import Hot from '../pages/Hot';
import Vip from '../pages/Dashboard/Vip';
import DashboardVideo from '../pages/Dashboard/Video';
import DashboardAudio from '../pages/Dashboard/Audio';
import DashboardCanvas from '../pages/Dashboard/CanvasWorkspace';

// 路由守卫组件
const ProtectedRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isLoading } = useAuth();
  const token = localStorage.getItem('token');

  // 如果正在加载，显示加载状态
  if (isLoading) {
    return (
      <div style={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        height: '100vh',
        fontSize: '16px',
        color: '#666'
      }}>
        加载中...
      </div>
    );
  }

  if (!token) {
    return <Navigate to="/" replace />;
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
    path: '/dashboard',
    element: (
      <ProtectedRoute>
        <Dashboard />
      </ProtectedRoute>
    ),
    children: [
      {
        index: true,
        element: <DashboardGenerate />,
      },
      {
        path: 'history',
        element: <History />,
      },
      {
        path: 'assets',
        element: <Assets />,
      },
      {
        path: 'hot',
        element: <Hot />,
      },
      {
        path: 'video',
        element: <DashboardVideo />,
      },
      {
        path: 'canvas',
        element: <DashboardCanvas />,
      },
      {
        path: 'audio',
        element: <DashboardAudio />,
      },
    ],
  },
  {
    path: '/dashboard/vip',
    element: (
      <ProtectedRoute>
        <Vip />
      </ProtectedRoute>
    ),
  },
  {
    path: '/canvas',
    element: (
      <ProtectedRoute>
        <Navigate to="/dashboard/canvas" replace />
      </ProtectedRoute>
    ),
  },
  {
    path: '/canvas/:workflowId',
    element: (
      <ProtectedRoute>
        <DashboardCanvas />
      </ProtectedRoute>
    ),
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
