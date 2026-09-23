import React, { useEffect } from 'react';
import { Link } from 'react-router-dom';

export const OAuthCallback: React.FC = () => {
  useEffect(() => {
    // Do not decode an unverified URL token into a logged-in user or persist it.
    window.history.replaceState(null, '', '/auth/callback');
    localStorage.removeItem('oauth_redirect_url');
  }, []);

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <div className="max-w-md bg-white rounded-lg shadow p-8 text-center">
        <h2 className="text-lg font-semibold mb-4">Google 로그인을 완료할 수 없습니다.</h2>
        <p className="text-gray-600 mb-6">
          현재 서버의 Google 로그인 리다이렉트는 API에 필요한 인증 쿠키를 설정하지 않습니다.
          쿠키 인증과 사용자 정보 조회 연동을 보완해야 합니다.
        </p>
        <Link to="/" className="text-blue-600 underline">일반 로그인으로 돌아가기</Link>
      </div>
    </div>
  );
};
