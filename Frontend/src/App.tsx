import React, { useState } from 'react';
import { BrowserRouter as Router } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from 'react-query';
import { Toaster } from 'react-hot-toast';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import { RoomProvider, useRoom } from './contexts/RoomContext';
import { LoginForm } from './components/auth/LoginForm';
import { SignupForm } from './components/auth/SignupForm';
import { CreateRoomForm } from './components/room/CreateRoomForm';
import { RoomList } from './components/room/RoomList';
import { RoomView } from './components/room/RoomView';
import { LoadingSpinner } from './components/common/LoadingSpinner';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

const AuthenticatedAppContent: React.FC = () => {
  const { user, logout } = useAuth();
  const { currentRoom } = useRoom();
  const [view, setView] = useState<'menu' | 'create' | 'join'>('menu');

  if (currentRoom) {
    return <RoomView />;
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="container mx-auto px-4 py-8">
        {/* Header */}
        <div className="flex justify-between items-center mb-8">
          <h1 className="text-3xl font-bold text-gray-800">🍽️ BobGourmet</h1>
          <div className="flex items-center space-x-4">
            <span className="text-gray-600">Welcome, {user?.username}!</span>
            <button
              onClick={logout}
              className="bg-gray-600 text-white px-4 py-2 rounded-md hover:bg-gray-700 focus:outline-none focus:ring-2 focus:ring-gray-500"
            >
              Logout
            </button>
          </div>
        </div>

        {/* Navigation */}
        {view === 'menu' && (
          <div className="max-w-md mx-auto space-y-4">
            <div className="bg-white p-8 rounded-lg shadow-md text-center">
              <h2 className="text-2xl font-bold mb-6">Choose an Option</h2>
              <div className="space-y-4">
                <button
                  onClick={() => setView('create')}
                  className="w-full bg-blue-600 text-white py-3 px-4 rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  Create New Room
                </button>
                <button
                  onClick={() => setView('join')}
                  className="w-full bg-green-600 text-white py-3 px-4 rounded-md hover:bg-green-700 focus:outline-none focus:ring-2 focus:ring-green-500"
                >
                  Browse & Join Rooms
                </button>
              </div>
            </div>
          </div>
        )}

        {view === 'create' && (
          <CreateRoomForm onCancel={() => setView('menu')} />
        )}

        {view === 'join' && (
          <RoomList onCancel={() => setView('menu')} />
        )}
      </div>
    </div>
  );
};

const AuthenticatedApp: React.FC = () => {
  return (
    <RoomProvider>
      <AuthenticatedAppContent />
    </RoomProvider>
  );
};

const UnauthenticatedApp: React.FC = () => {
  const [isLogin, setIsLogin] = useState(true);

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center">
      <div className="container mx-auto px-4">
        <div className="text-center mb-8">
          <h1 className="text-4xl font-bold text-gray-800 mb-2">🍽️ BobGourmet</h1>
          <p className="text-gray-600">Restaurant selection through multiplayer voting</p>
        </div>

        {isLogin ? (
          <LoginForm onSwitchToSignup={() => setIsLogin(false)} />
        ) : (
          <SignupForm onSwitchToLogin={() => setIsLogin(true)} />
        )}
      </div>
    </div>
  );
};

const AppContent: React.FC = () => {
  const { user, isLoading } = useAuth();

  if (isLoading) {
    return <LoadingSpinner />;
  }

  return user ? <AuthenticatedApp /> : <UnauthenticatedApp />;
};

const App: React.FC = () => {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <Router>
          <div className="App">
            <AppContent />
            <Toaster 
              position="top-right"
              toastOptions={{
                duration: 4000,
                style: {
                  background: '#363636',
                  color: '#fff',
                },
              }}
            />
          </div>
        </Router>
      </AuthProvider>
    </QueryClientProvider>
  );
};

export default App;