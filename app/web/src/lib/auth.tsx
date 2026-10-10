import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { api, getToken, setToken, type Me } from "./api";

interface AuthState {
  me: Me | null;
  loading: boolean;
  login: (loginId: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<Me | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!getToken()) {
      setLoading(false);
      return;
    }
    api
      .get<Me>("/api/me")
      .then(setMe)
      .catch(() => {
        setToken(null);
      })
      .finally(() => setLoading(false));
  }, []);

  const value = useMemo<AuthState>(
    () => ({
      me,
      loading,
      async login(loginId, password) {
        const res = await api.post<{ token: string }>("/api/auth/login", { loginId, password });
        setToken(res.token);
        const profile = await api.get<Me>("/api/me");
        setMe(profile);
      },
      logout() {
        setToken(null);
        setMe(null);
      },
    }),
    [me, loading],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
