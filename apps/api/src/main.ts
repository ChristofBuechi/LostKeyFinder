import 'reflect-metadata';
import { ValidationPipe } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { NestFactory } from '@nestjs/core';
import { FastifyAdapter, NestFastifyApplication } from '@nestjs/platform-fastify';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';
import { randomUUID } from 'node:crypto';
import { AppModule } from './app.module';
import { ProblemDetailsFilter } from './common/filters/problem-details.filter';

async function bootstrap(): Promise<void> {
  const app = await NestFactory.create<NestFastifyApplication>(AppModule, new FastifyAdapter({ logger: false }));
  const config = app.get(ConfigService);
  const prefix = config.get<string>('app.apiPrefix', 'api');
  const version = config.get<string>('app.apiVersion', 'v1');

  app.setGlobalPrefix(`${prefix}/${version}`);
  app.enableCors({ origin: config.get<string[]>('app.allowedOrigins', []), credentials: false });
  app.useGlobalPipes(new ValidationPipe({ whitelist: true, forbidNonWhitelisted: true, transform: true }));
  app.useGlobalFilters(new ProblemDetailsFilter());
  app.getHttpAdapter().getInstance().addHook('onRequest', async (request) => {
    request.id = request.headers['x-correlation-id']?.toString() || randomUUID();
  });

  const swaggerConfig = new DocumentBuilder()
    .setTitle('Lost Key Finder API')
    .setDescription('Versioned API for Lost Key Finder')
    .setVersion(version)
    .build();
  SwaggerModule.createDocument(app, swaggerConfig);

  await app.listen(config.get<number>('app.port', 3000), '0.0.0.0');
}

void bootstrap();
