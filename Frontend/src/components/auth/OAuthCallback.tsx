import React, { useEffect, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { oauthCallbackIssue } from '../../services/authNavigation';
import { LoadingSpinner } from '../common/LoadingSpinner';

export const OAuthCallback: React.FC = () => {
  const { user, isLoading, authError, refreshUser } = useAuth();
  const [issue] = useState(() => oauthCallbackIssue(window.location.search));

  useEffect(() => {
    // Strip old credentials/errors from history without trusting them as identity or redirect URLs.
    window.history.replaceState(window.history.state, '', '/auth/callback');
    try { localStorage.removeItem('oauth_redirect_url'); } catch { /* Storage can be disabled. */ }
  }, []);

  if (!issue && isLoading) return <LoadingSpinner />;
  if (!issue && user) return <Navigate to="/" replace />;

  const message: string = issue === 'provider'
    ? 'Google 인증이 취소되었거나 서버에서 로그인을 완료하지 못했습니다.'
    : issue === 'legacy'
      ? '이전 방식의 로그인 링크는 사용할 수 없습니다. Google 로그인을 다시 시작해주세요.'
      : authError || '로그인 쿠키를 확인하지 못했습니다. Google 로그인을 다시 시작해주세요.';

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <div className="max-w-md bg-white rounded-lg shadow p-8 text-center">
        <h2 className="text-lg font-semibold mb-4">Google 로그인을 완료할 수 없습니다.</h2>
        <p role="alert" className="text-gray-600 mb-6">{message}</p>
        {!issue && <button type="button" onClick={() => { void refreshUser(); }}
          className="block mx-auto mb-4 text-blue-600 underline">로그인 상태 다시 확인</button>}
        <Link to="/" className="text-blue-600 underline">홈으로 돌아가기</Link>
      </div>
    </div>
  );
};
