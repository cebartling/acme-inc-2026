export interface ApiResponse<T = unknown> {
  status: number;
  data: T;
  headers: {
    'set-cookie'?: string[];
    [key: string]: string | string[] | undefined;
  };
}

export interface RequestOptions {
  headers?: Record<string, string>;
  timeout?: number;
  redirect?: 'follow' | 'manual' | 'error';
}

export class ApiClient {
  private baseUrl: string;
  private defaultHeaders: Record<string, string>;
  private authToken: string | null = null;

  constructor(baseUrl: string, defaultHeaders?: Record<string, string>) {
    this.baseUrl = baseUrl.replace(/\/$/, ''); // Remove trailing slash
    this.defaultHeaders = {
      'Content-Type': 'application/json',
      Accept: 'application/json',
      ...defaultHeaders,
    };
  }

  /**
   * Sets a default header that will be included in all requests.
   */
  setDefaultHeader(name: string, value: string): void {
    this.defaultHeaders[name] = value;
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
        redirect: options.redirect || 'follow',
      });

      clearTimeout(timeoutId);

      let data: T;
      const contentType = response.headers.get('content-type');

      if (contentType?.includes('application/json')) {
        data = (await response.json()) as T;
      } else {
        data = (await response.text()) as unknown as T;
      }

      // Convert Headers to plain object with set-cookie as array
      const headersObj: {
        'set-cookie'?: string[];
        [key: string]: string | string[] | undefined;
      } = {};

      // Get all Set-Cookie headers (there can be multiple)
      const setCookies = response.headers.getSetCookie?.() || [];
      if (setCookies.length > 0) {
        headersObj['set-cookie'] = setCookies;
      }

      // Get other headers
      response.headers.forEach((value, key) => {
        if (key.toLowerCase() !== 'set-cookie') {
          headersObj[key] = value;
        }
      });

      return {
        status: response.status,
        data,
        headers: headersObj,
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
