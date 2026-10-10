import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../lib/auth";

export default function Login() {
  const { login, me } = useAuth();
  const nav = useNavigate();
  const [loginId, setLoginId] = useState("emp1");
  const [password, setPassword] = useState("password");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (me) {
    nav("/");
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await login(loginId, password);
      nav("/");
    } catch (err) {
      setError(err instanceof Error ? err.message : "로그인 실패");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="container" style={{ maxWidth: 380 }}>
      <h2>로그인</h2>
      <div className="card">
        <form onSubmit={onSubmit}>
          <label>아이디</label>
          <input value={loginId} onChange={(e) => setLoginId(e.target.value)} autoFocus />
          <label>비밀번호</label>
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
          {error && <div className="error">{error}</div>}
          <div style={{ marginTop: 14 }}>
            <button type="submit" disabled={busy}>
              {busy ? "로그인 중…" : "로그인"}
            </button>
          </div>
        </form>
        <p className="muted" style={{ fontSize: 12, marginTop: 14 }}>
          개발용 계정: admin / approver1 / approver2 / emp1 (비밀번호 password)
        </p>
      </div>
    </div>
  );
}
