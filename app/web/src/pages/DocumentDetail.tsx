import { useEffect, useRef, useState } from "react";
import { useParams } from "react-router-dom";
import { api, uploadFile, getToken, type DocumentDetail as Detail } from "../lib/api";
import { useAuth } from "../lib/auth";

export default function DocumentDetail() {
  const { id } = useParams();
  const docId = Number(id);
  const { me } = useAuth();
  const [doc, setDoc] = useState<Detail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  function load() {
    api.get<Detail>(`/api/documents/${docId}`).then(setDoc).catch((e) => setError(e.message));
  }
  useEffect(load, [docId]);

  async function act(action: string) {
    setError(null);
    setBusy(true);
    try {
      await api.post(`/api/documents/${docId}/${action}`);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "처리 실패");
    } finally {
      setBusy(false);
    }
  }

  async function onUpload() {
    const file = fileRef.current?.files?.[0];
    if (!file) return;
    setBusy(true);
    setError(null);
    try {
      await uploadFile(docId, file);
      if (fileRef.current) fileRef.current.value = "";
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "업로드 실패");
    } finally {
      setBusy(false);
    }
  }

  function download(attId: number) {
    // 서명 URL로 302 리다이렉트. Authorization 헤더가 필요하므로 fetch 후 location을 연다.
    fetch(`/api/attachments/${attId}`, {
      headers: { Authorization: `Bearer ${getToken()}` },
      redirect: "manual",
    }).then((res) => {
      const loc = res.headers.get("Location");
      if (loc) window.open(loc, "_blank");
      else setError("다운로드 URL을 받지 못했습니다 (CORS/프록시 확인)");
    });
  }

  if (error && !doc) return <div className="error">{error}</div>;
  if (!doc) return <p>불러오는 중…</p>;

  const isDrafter = me?.id === doc.drafterId;
  const nextPending = doc.lines.find((l) => l.status === "PENDING");
  const canAct = doc.status === "SUBMITTED" && nextPending?.approverId === me?.id;

  return (
    <>
      <div className="row" style={{ justifyContent: "space-between" }}>
        <h2>
          {doc.type === "LEAVE" ? "휴가" : "결재"} · {doc.title}
        </h2>
        <span className={`badge ${doc.status}`}>{doc.status}</span>
      </div>
      {error && <div className="error">{error}</div>}

      <div className="card">
        {doc.body && <p style={{ whiteSpace: "pre-wrap" }}>{doc.body}</p>}
        {doc.leave && (
          <p className="muted">
            {doc.leave.leaveType} · {doc.leave.startDate} ~ {doc.leave.endDate}
          </p>
        )}
        <div className="row" style={{ marginTop: 12 }}>
          {isDrafter && doc.status === "DRAFT" && (
            <button disabled={busy} onClick={() => act("submit")}>
              상신
            </button>
          )}
          {canAct && (
            <>
              <button disabled={busy} onClick={() => act("approve")}>
                승인
              </button>
              <button className="danger" disabled={busy} onClick={() => act("reject")}>
                반려
              </button>
            </>
          )}
        </div>
      </div>

      <div className="card">
        <h3>결재선</h3>
        <table>
          <thead>
            <tr>
              <th>단계</th>
              <th>결재자 ID</th>
              <th>상태</th>
              <th>처리 시각</th>
            </tr>
          </thead>
          <tbody>
            {doc.lines.map((l) => (
              <tr key={l.step}>
                <td>{l.step}</td>
                <td>{l.approverId}</td>
                <td>
                  <span className={`badge ${l.status}`}>{l.status}</span>
                </td>
                <td className="muted">{l.actedAt ? new Date(l.actedAt).toLocaleString() : "-"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="card">
        <h3>첨부파일</h3>
        {doc.attachments.length === 0 && <p className="muted">없음</p>}
        {doc.attachments.map((a) => (
          <div key={a.id} className="row" style={{ justifyContent: "space-between" }}>
            <div>
              <button className="secondary" onClick={() => download(a.id)}>
                {a.fileName}
              </button>{" "}
              <span className="muted">({a.size} bytes)</span>
              <div className="mono">sha256: {a.sha256}</div>
            </div>
          </div>
        ))}
        {isDrafter && (
          <div className="row" style={{ marginTop: 10 }}>
            <input type="file" ref={fileRef} />
            <button disabled={busy} onClick={onUpload}>
              업로드
            </button>
          </div>
        )}
      </div>

      <div className="card">
        <h3>결재 이력 (해시 체인)</h3>
        <table>
          <thead>
            <tr>
              <th>seq</th>
              <th>행위</th>
              <th>행위자</th>
              <th>시각</th>
              <th>hash</th>
            </tr>
          </thead>
          <tbody>
            {doc.history.map((h) => (
              <tr key={h.seq}>
                <td>{h.seq}</td>
                <td>{h.action}</td>
                <td>{h.actorId}</td>
                <td className="muted">{new Date(h.actedAt).toLocaleString()}</td>
                <td className="mono">{h.hash.slice(0, 16)}…</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
