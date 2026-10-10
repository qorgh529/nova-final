import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, type DocumentDetail } from "../lib/api";

export default function DocumentCreate() {
  const nav = useNavigate();
  const [type, setType] = useState<"APPROVAL" | "LEAVE">("APPROVAL");
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [approverIds, setApproverIds] = useState("2,3");
  const [leaveType, setLeaveType] = useState("ANNUAL");
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const ids = approverIds
        .split(",")
        .map((s) => Number(s.trim()))
        .filter((n) => Number.isFinite(n) && n > 0);
      if (ids.length === 0) throw new Error("결재자 ID를 입력하세요 (쉼표 구분)");
      const payload: Record<string, unknown> = { type, title, body, approverIds: ids };
      if (type === "LEAVE") {
        if (!startDate || !endDate) throw new Error("휴가 기간을 입력하세요");
        payload.leave = { leaveType, startDate, endDate };
      }
      const doc = await api.post<DocumentDetail>("/api/documents", payload);
      nav(`/documents/${doc.id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "작성 실패");
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <h2>새 문서</h2>
      <div className="card">
        <form onSubmit={onSubmit}>
          <label>종류</label>
          <select value={type} onChange={(e) => setType(e.target.value as "APPROVAL" | "LEAVE")}>
            <option value="APPROVAL">일반 결재</option>
            <option value="LEAVE">휴가 신청</option>
          </select>

          <label>제목</label>
          <input value={title} onChange={(e) => setTitle(e.target.value)} autoFocus />

          <label>내용</label>
          <textarea value={body} onChange={(e) => setBody(e.target.value)} />

          {type === "LEAVE" && (
            <>
              <label>휴가 종류</label>
              <select value={leaveType} onChange={(e) => setLeaveType(e.target.value)}>
                <option value="ANNUAL">연차</option>
                <option value="SICK">병가</option>
                <option value="OTHER">기타</option>
              </select>
              <div className="row">
                <div style={{ flex: 1 }}>
                  <label>시작일</label>
                  <input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} />
                </div>
                <div style={{ flex: 1 }}>
                  <label>종료일</label>
                  <input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} />
                </div>
              </div>
            </>
          )}

          <label>결재선 (결재자 ID, 쉼표 구분 · 순서대로)</label>
          <input value={approverIds} onChange={(e) => setApproverIds(e.target.value)} placeholder="2,3" />

          {error && <div className="error">{error}</div>}
          <div style={{ marginTop: 14 }} className="row">
            <button type="submit" disabled={busy}>
              {busy ? "작성 중…" : "작성"}
            </button>
            <button type="button" className="secondary" onClick={() => nav("/")}>
              취소
            </button>
          </div>
        </form>
      </div>
    </>
  );
}
