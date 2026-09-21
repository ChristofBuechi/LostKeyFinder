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

    if (status >= 500) {
      this.logger.error(`${request.method} ${request.url}`, exception instanceof Error ? exception.stack : exception);
    }

    reply.status(status).type('application/problem+json').send({
      type: `https://lostkeyfinder.example/problems/${status}`,
      title: HttpStatus[status] ?? 'Error',
      status,
      detail: status < 500 && typeof detail === 'string' ? detail : undefined,
      instance: request.url,
      correlationId: request.id,
    });
  }
}
