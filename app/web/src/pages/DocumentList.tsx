import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, type DocumentSummary } from "../lib/api";

export default function DocumentList() {
  const [docs, setDocs] = useState<DocumentSummary[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .get<DocumentSummary[]>("/api/documents")
      .then(setDocs)
      .catch((e) => setError(e.message));
  }, []);

  return (
    <>
      <div className="row" style={{ justifyContent: "space-between" }}>
        <h2>내 문서</h2>
        <Link to="/documents/new">
          <button>새 문서</button>
        </Link>
      </div>
      {error && <div className="error">{error}</div>}
      <div className="card">
        {docs.length === 0 ? (
          <p className="muted">문서가 없습니다.</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>번호</th>
                <th>종류</th>
                <th>제목</th>
                <th>상태</th>
                <th>작성일</th>
              </tr>
            </thead>
            <tbody>
              {docs.map((d) => (
                <tr key={d.id}>
                  <td>{d.id}</td>
                  <td>{d.type === "LEAVE" ? "휴가" : "결재"}</td>
                  <td>
                    <Link to={`/documents/${d.id}`}>{d.title}</Link>
                  </td>
                  <td>
                    <span className={`badge ${d.status}`}>{d.status}</span>
                  </td>
                  <td className="muted">{new Date(d.createdAt).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </>
  );
}
