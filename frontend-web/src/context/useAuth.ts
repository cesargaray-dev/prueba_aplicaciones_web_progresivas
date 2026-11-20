import { useContext } from 'react';
import { AuthContext } from './authContextInstance';
import type { AuthContextType } from './authContextInstance';

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
