import { ValidationPipe } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { NestFastifyApplication } from '@nestjs/platform-fastify';
import { ProblemDetailsFilter } from './common/filters/problem-details.filter';

export function configureApplication(app: NestFastifyApplication): void {
  const allowedOrigins = app.get(ConfigService)
    .get<string>('ALLOWED_ORIGINS', 'http://localhost:4200')
    .split(',')
    .map((origin) => origin.trim());
  const fastify = app.getHttpAdapter().getInstance();

  app.setGlobalPrefix('api/v1');
  app.enableCors({ origin: allowedOrigins, credentials: false });
  app.useGlobalPipes(new ValidationPipe({ whitelist: true, forbidNonWhitelisted: true, transform: true }));
  app.useGlobalFilters(new ProblemDetailsFilter());
  app.enableShutdownHooks();

  fastify.addHook('onSend', async (request, reply) => {
    reply.header('x-correlation-id', request.id);
  });
}
