import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// 개발 서버는 API로 프록시한다. 운영 이미지는 nginx가 같은 경로를 프록시한다.
const apiTarget = process.env.VITE_API_TARGET ?? "http://localhost:8080";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": { target: apiTarget, changeOrigin: true },
      "/version": { target: apiTarget, changeOrigin: true },
    },
  },
});
