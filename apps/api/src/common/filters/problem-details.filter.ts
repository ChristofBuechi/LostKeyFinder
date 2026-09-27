import {
  ArgumentsHost,
  Catch,
  ExceptionFilter,
  HttpException,
  HttpStatus,
  Logger,
} from '@nestjs/common';
import { FastifyReply, FastifyRequest } from 'fastify';

const problems: Record<number, { name: string; title: string }> = {
  400: { name: 'invalid-request', title: 'Invalid request' },
  401: { name: 'unauthorized', title: 'Unauthorized' },
  403: { name: 'forbidden', title: 'Forbidden' },
  404: { name: 'not-found', title: 'Not found' },
  409: { name: 'conflict', title: 'Conflict' },
  429: { name: 'rate-limit-exceeded', title: 'Too many requests' },
  500: { name: 'internal-server-error', title: 'Internal server error' },
  503: { name: 'dependency-unavailable', title: 'Service unavailable' },
};
const requestFailedProblem = { name: 'request-failed', title: 'Request failed' };

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
    const detail = typeof response === 'string'
      ? response
      : typeof response === 'object' && response !== null && 'message' in response
        ? (response as { message: unknown }).message
        : undefined;
    const path = new URL(request.url, 'http://localhost').pathname;
    const problem = problems[status] ?? (status >= 500 ? problems[500] : requestFailedProblem);

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
      type: `/problems/${problem.name}`,
      title: problem.title,
      status,
      detail: status < 500
        ? (Array.isArray(detail) ? 'Request validation failed' : typeof detail === 'string' ? detail : undefined)
        : undefined,
      errors: status < 500 && Array.isArray(detail) ? detail : undefined,
      instance: path,
      correlationId: request.id,
    });
  }

}
