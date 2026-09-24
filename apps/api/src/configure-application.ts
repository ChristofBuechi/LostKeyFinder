import { ValidationPipe } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { NestFastifyApplication } from '@nestjs/platform-fastify';
import { randomUUID } from 'node:crypto';
import { ProblemDetailsFilter } from './common/filters/problem-details.filter';

const correlationIdPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export function configureApplication(app: NestFastifyApplication): string {
  const config = app.get(ConfigService);
  const apiPath = `${config.get<string>('app.apiPrefix', 'api')}/${config.get<string>('app.apiVersion', 'v1')}`;
  const fastify = app.getHttpAdapter().getInstance();

  app.setGlobalPrefix(apiPath);
  app.enableCors({ origin: config.get<string[]>('app.allowedOrigins', []), credentials: false });
  app.useGlobalPipes(new ValidationPipe({ whitelist: true, forbidNonWhitelisted: true, transform: true }));
  app.useGlobalFilters(new ProblemDetailsFilter());
  app.enableShutdownHooks();

  fastify.addHook('onRequest', async (request) => {
    const suppliedId = request.headers['x-correlation-id']?.toString();
    request.id = suppliedId && correlationIdPattern.test(suppliedId) ? suppliedId : randomUUID();
  });
  fastify.addHook('onSend', async (request, reply) => {
    reply.header('x-correlation-id', request.id);
  });

  return apiPath;
}
