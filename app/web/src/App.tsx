import { useEffect, useState } from "react";
import { Link, Navigate, Route, Routes, useNavigate } from "react-router-dom";
import { useAuth } from "./lib/auth";
import { api, type VersionInfo } from "./lib/api";
import Login from "./pages/Login";
import DocumentList from "./pages/DocumentList";
import DocumentCreate from "./pages/DocumentCreate";
import DocumentDetail from "./pages/DocumentDetail";
import Admin from "./pages/Admin";

function VersionBadge() {
  const [v, setV] = useState<VersionInfo | null>(null);
  useEffect(() => {
    api.get<VersionInfo>("/version").then(setV).catch(() => {});
  }, []);
  if (!v) return null;
  return (
    <span className="ver" title={`image ${v.imageDigest}`}>
      {v.name} {v.version} · {v.commit} · {v.cloud}
    </span>
  );
}

function Shell({ children }: { children: React.ReactNode }) {
  const { me, logout } = useAuth();
  const nav = useNavigate();
  return (
    <>
      <div className="topbar">
        <span className="brand">
          <Link to="/">사내 전자결재</Link>
        </span>
        {me?.roles.includes("ADMIN") && <Link to="/admin">관리</Link>}
        <span className="spacer" />
        <VersionBadge />
        {me && (
          <>
            <span className="muted">
              {me.name} ({me.roles.join(", ")})
            </span>
            <button
              className="secondary"
              onClick={() => {
                logout();
                nav("/login");
              }}
            >
              로그아웃
            </button>
          </>
        )}
      </div>
      <div className="container">{children}</div>
    </>
  );
}

function RequireAuth({ children }: { children: React.ReactNode }) {
  const { me, loading } = useAuth();
  if (loading) return <div className="container">불러오는 중…</div>;
  if (!me) return <Navigate to="/login" replace />;
  return <Shell>{children}</Shell>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/" element={<RequireAuth><DocumentList /></RequireAuth>} />
      <Route path="/documents/new" element={<RequireAuth><DocumentCreate /></RequireAuth>} />
      <Route path="/documents/:id" element={<RequireAuth><DocumentDetail /></RequireAuth>} />
      <Route path="/admin" element={<RequireAuth><Admin /></RequireAuth>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
