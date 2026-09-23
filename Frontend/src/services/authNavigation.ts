export function googleLoginUrl(backendBaseUrl?: string): string {
  return (backendBaseUrl || '').replace(/\/$/, '') + '/oauth2/authorization/google';
}

export function oauthCallbackIssue(search: string): 'provider' | 'legacy' | null {
  const params = new URLSearchParams(search);
  if (params.has('error')) return 'provider';
  // The browser never receives or exchanges codes/tokens in the new backend-owned flow.
  if (params.has('token') || params.has('code') || params.has('access_token')) return 'legacy';
  return null;
}
