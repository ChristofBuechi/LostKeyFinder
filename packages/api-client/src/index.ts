export interface ApiProblemDetails {
  type: string;
  title: string;
  status: number;
  detail?: string;
  instance?: string;
  correlationId?: string;
}

export interface ApiVersionResponse {
  version: string;
  environment: string;
}

export * from './generated';
