import {
  ArgumentsHost,
  Catch,
  ExceptionFilter,
  HttpException,
  HttpStatus,
  Logger,
} from '@nestjs/common';
import { FastifyReply, FastifyRequest } from 'fastify';

@Catch()
export class ProblemDetailsFilter implements ExceptionFilter {
  private readonly logger = new Logger(ProblemDetailsFilter.name);

  catch(exception: unknown, host: ArgumentsHost): void {
    const context = host.switchToHttp();
    const request = context.getRequest<FastifyRequest>();
    const reply = context.getResponse<FastifyReply>();
    const status = exception instanceof HttpException
      ? exception.getStatus()
      : HttpStatus.INTERNAL_SERVER_ERROR;
    const response = exception instanceof HttpException ? exception.getResponse() : undefined;
    const detail = typeof response === 'object' && response !== null && 'message' in response
      ? (response as { message: unknown }).message
      : undefined;
    const path = new URL(request.url, 'http://localhost').pathname;
    const problemName = this.problemName(status);

    if (status >= 500) {
      this.logger.error(JSON.stringify({
        event: 'request_failed',
        method: request.method,
        path,
        status,
        correlationId: request.id,
        errorType: exception instanceof Error ? exception.name : 'UnknownError',
      }));
    }

    reply.status(status).type('application/problem+json').send({
      type: `/problems/${problemName}`,
      title: this.problemTitle(status),
      status,
      detail: status < 500
        ? (Array.isArray(detail) ? 'Request validation failed' : typeof detail === 'string' ? detail : undefined)
        : undefined,
      errors: status < 500 && Array.isArray(detail) ? detail : undefined,
      instance: path,
      correlationId: request.id,
    });
  }

  private problemName(status: number): string {
    return ({
      400: 'invalid-request',
      401: 'unauthorized',
      403: 'forbidden',
      404: 'not-found',
      409: 'conflict',
      429: 'rate-limit-exceeded',
      503: 'dependency-unavailable',
    } as Record<number, string>)[status] ?? 'internal-server-error';
  }

  private problemTitle(status: number): string {
    return ({
      400: 'Invalid request',
      401: 'Unauthorized',
      403: 'Forbidden',
      404: 'Not found',
      409: 'Conflict',
      429: 'Too many requests',
      500: 'Internal server error',
      503: 'Service unavailable',
    } as Record<number, string>)[status] ?? (HttpStatus[status] ?? 'Request failed');
  }
}
