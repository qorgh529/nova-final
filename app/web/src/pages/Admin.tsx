import { useEffect, useState } from "react";
import { api, type Integrity } from "../lib/api";

interface AdminUser {
  id: number;
  loginId: string;
  name: string;
  department: string;
  roles: string[];
  createdAt: string;
}

export default function Admin() {
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [integrity, setIntegrity] = useState<Integrity | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.get<AdminUser[]>("/api/admin/users").then(setUsers).catch((e) => setError(e.message));
  }, []);

  function checkIntegrity() {
    setError(null);
    api.get<Integrity>("/api/admin/integrity").then(setIntegrity).catch((e) => setError(e.message));
  }

  return (
    <>
      <h2>관리</h2>
      {error && <div className="error">{error}</div>}

      <div className="card">
        <div className="row" style={{ justifyContent: "space-between" }}>
          <h3>결재 이력 무결성 검증</h3>
          <button onClick={checkIntegrity}>검증 실행</button>
        </div>
        {integrity && (
          <div style={{ marginTop: 10 }}>
            <span className={`badge ${integrity.valid ? "ok" : "bad"}`}>
              {integrity.valid ? "정상" : "위변조 의심"}
            </span>{" "}
            <span className="muted">
              {integrity.valid
                ? `${integrity.count}건 검증됨`
                : `seq ${integrity.brokenAtSeq}에서 문제: ${integrity.reason}`}
            </span>
            {integrity.headHash && <div className="mono">head: {integrity.headHash}</div>}
          </div>
        )}
      </div>

      <div className="card">
        <h3>사용자</h3>
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>로그인</th>
              <th>이름</th>
              <th>부서</th>
              <th>역할</th>
              <th>생성일</th>
            </tr>
          </thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.id}>
                <td>{u.id}</td>
                <td>{u.loginId}</td>
                <td>{u.name}</td>
                <td>{u.department}</td>
                <td>
                  {u.roles.map((r) => (
                    <span key={r} className="badge" style={{ marginRight: 4 }}>
                      {r}
                    </span>
                  ))}
                </td>
                <td className="muted">{new Date(u.createdAt).toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
