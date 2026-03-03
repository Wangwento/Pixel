import React, { createContext, useContext, useState, useEffect, useCallback, ReactNode } from 'react';
import { getCurrentUser } from '../api/auth';
import type { UserInfo } from '../api/auth';

interface AuthContextType {
  currentUser: UserInfo | null;
  setCurrentUser: (user: UserInfo | null) => void;
  refreshUser: () => Promise<void>;
  logout: () => void;
  isLoading: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return context;
};

interface AuthProviderProps {
  children: ReactNode;
}

export const AuthProvider: React.FC<AuthProviderProps> = ({ children }) => {
  const [currentUser, setCurrentUser] = useState<UserInfo | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const refreshUser = useCallback(async () => {
    const token = localStorage.getItem('token');
    console.log('[AuthContext] refreshUser called, token:', token ? 'exists' : 'null');

    if (!token) {
      setCurrentUser(null);
      setIsLoading(false);
      return;
    }

    try {
      console.log('[AuthContext] Fetching user info...');
      const res = await getCurrentUser();
      const response = res as unknown as { code: number; data: UserInfo };
      console.log('[AuthContext] User info response:', response);
      console.log('[AuthContext] User data:', response.data);
      console.log('[AuthContext] freeQuota:', response.data?.freeQuota);
      console.log('[AuthContext] monthlyQuota:', response.data?.monthlyQuota);
      console.log('[AuthContext] monthlyQuotaUsed:', response.data?.monthlyQuotaUsed);

      if (response.code === 200) {
        setCurrentUser(response.data);
        console.log('[AuthContext] User info loaded successfully:', response.data.username);
      } else {
        console.warn('[AuthContext] Invalid response code:', response.code);
        localStorage.removeItem('token');
        setCurrentUser(null);
      }
    } catch (error) {
      console.error('[AuthContext] Failed to fetch user info:', error);
      localStorage.removeItem('token');
      setCurrentUser(null);
    } finally {
      setIsLoading(false);
      console.log('[AuthContext] Loading complete');
    }
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem('token');
    setCurrentUser(null);
  }, []);

  useEffect(() => {
    refreshUser();

    // 监听storage事件，实现跨标签页同步
    const handleStorageChange = (e: StorageEvent) => {
      if (e.key === 'token') {
        refreshUser();
      }
    };

    window.addEventListener('storage', handleStorageChange);
    return () => window.removeEventListener('storage', handleStorageChange);
  }, [refreshUser]);

  return (
    <AuthContext.Provider value={{ currentUser, setCurrentUser, refreshUser, logout, isLoading }}>
      {children}
    </AuthContext.Provider>
  );
};