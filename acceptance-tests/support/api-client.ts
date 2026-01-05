export interface ApiResponse<T = unknown> {
  status: number;
  data: T;
  headers: Headers;
}

export interface RequestOptions {
  headers?: Record<string, string>;
  timeout?: number;
}

export class ApiClient {
  private baseUrl: string;
  private defaultHeaders: Record<string, string>;
  private authToken: string | null = null;

  constructor(baseUrl: string) {
    this.baseUrl = baseUrl.replace(/\/$/, ''); // Remove trailing slash
    this.defaultHeaders = {
      'Content-Type': 'application/json',
      Accept: 'application/json',
    };
  }

  setAuthToken(token: string): void {
    this.authToken = token;
  }

  clearAuthToken(): void {
    this.authToken = null;
  }

  private getHeaders(customHeaders?: Record<string, string>): Record<string, string> {
    const headers = { ...this.defaultHeaders, ...customHeaders };

    if (this.authToken) {
      headers['Authorization'] = `Bearer ${this.authToken}`;
    }

    return headers;
  }

  async get<T>(path: string, options: RequestOptions = {}): Promise<ApiResponse<T>> {
    return this.request<T>('GET', path, undefined, options);
  }

  async post<T>(
    path: string,
    body?: unknown,
    options: RequestOptions = {}
  ): Promise<ApiResponse<T>> {
    return this.request<T>('POST', path, body, options);
  }

  async put<T>(
    path: string,
    body?: unknown,
    options: RequestOptions = {}
  ): Promise<ApiResponse<T>> {
    return this.request<T>('PUT', path, body, options);
  }

  async patch<T>(
    path: string,
    body?: unknown,
    options: RequestOptions = {}
  ): Promise<ApiResponse<T>> {
    return this.request<T>('PATCH', path, body, options);
  }

  async delete<T>(path: string, options: RequestOptions = {}): Promise<ApiResponse<T>> {
    return this.request<T>('DELETE', path, undefined, options);
  }

  private async request<T>(
    method: string,
    path: string,
    body?: unknown,
    options: RequestOptions = {}
  ): Promise<ApiResponse<T>> {
    const url = `${this.baseUrl}${path.startsWith('/') ? path : `/${path}`}`;
    const headers = this.getHeaders(options.headers);

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), options.timeout || 30000);

    try {
      const response = await fetch(url, {
        method,
        headers,
        body: body ? JSON.stringify(body) : undefined,
        signal: controller.signal,
      });

      clearTimeout(timeoutId);

      let data: T;
      const contentType = response.headers.get('content-type');

      if (contentType?.includes('application/json')) {
        data = (await response.json()) as T;
      } else {
        data = (await response.text()) as unknown as T;
      }

      return {
        status: response.status,
        data,
        headers: response.headers,
      };
    } catch (error) {
      clearTimeout(timeoutId);
      throw error;
    }
  }

  async healthCheck(): Promise<boolean> {
    try {
      const response = await this.get('/actuator/health');
      return response.status === 200;
    } catch {
      return false;
    }
  }
}
