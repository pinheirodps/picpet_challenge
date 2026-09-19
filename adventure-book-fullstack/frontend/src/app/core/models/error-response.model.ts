/** Mirrors the backend's ErrorResponse — the uniform shape every failed API call returns. */
export interface ApiErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  messages: string[];
}
