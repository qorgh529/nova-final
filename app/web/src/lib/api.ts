// API 클라이언트. 토큰은 localStorage에 둔다 (데모용). 운영에서는 httpOnly 쿠키 등을 검토한다.

const TOKEN_KEY = "nova.token";

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string | null) {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
}

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {};
  const token = getToken();
  if (token) headers["Authorization"] = `Bearer ${token}`;
  if (body !== undefined) headers["Content-Type"] = "application/json";

  const res = await fetch(path, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (res.status === 204) return undefined as T;
  const text = await res.text();
  const data = text ? JSON.parse(text) : undefined;
  if (!res.ok) {
    const message = (data && (data.message || data.error)) || `HTTP ${res.status}`;
    throw new ApiError(res.status, message);
  }
  return data as T;
}

export const api = {
  get: <T>(path: string) => request<T>("GET", path),
  post: <T>(path: string, body?: unknown) => request<T>("POST", path, body),
};

export async function uploadFile(documentId: number, file: File) {
  const form = new FormData();
  form.append("file", file);
  const headers: Record<string, string> = {};
  const token = getToken();
  if (token) headers["Authorization"] = `Bearer ${token}`;
  const res = await fetch(`/api/documents/${documentId}/attachments`, {
    method: "POST",
    headers,
    body: form,
  });
  if (!res.ok) {
    const text = await res.text();
    throw new ApiError(res.status, text || `HTTP ${res.status}`);
  }
  return res.json();
}

// 타입
export interface LoginResponse {
  token: string;
  userId: number;
  name: string;
  roles: string[];
}

export interface Me {
  id: number;
  loginId: string;
  name: string;
  department: string;
  roles: string[];
}

export interface DocumentSummary {
  id: number;
  type: string;
  title: string;
  status: string;
  drafterId: number;
  createdAt: string;
}

export interface Leave {
  leaveType: string;
  startDate: string;
  endDate: string;
}

export interface ApprovalLine {
  step: number;
  approverId: number;
  status: string;
  actedAt: string | null;
}

export interface History {
  seq: number;
  documentId: number;
  actorId: number;
  action: string;
  actedAt: string;
  hash: string;
}

export interface Attachment {
  id: number;
  fileName: string;
  contentType: string;
  size: number;
  sha256: string;
  uploadedAt: string;
}

export interface DocumentDetail {
  id: number;
  type: string;
  title: string;
  body: string | null;
  status: string;
  drafterId: number;
  createdAt: string;
  updatedAt: string;
  leave: Leave | null;
  lines: ApprovalLine[];
  history: History[];
  attachments: Attachment[];
}

export interface Integrity {
  valid: boolean;
  count: number;
  headHash: string | null;
  brokenAtSeq: number | null;
  reason: string | null;
}

export interface VersionInfo {
  name: string;
  version: string;
  commit: string;
  buildTime: string;
  imageDigest: string;
  cloud: string;
}
