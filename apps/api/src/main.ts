import 'reflect-metadata';
import { ConfigService } from '@nestjs/config';
import { NestFactory } from '@nestjs/core';
import { FastifyAdapter, NestFastifyApplication } from '@nestjs/platform-fastify';
import { AppModule } from './app.module';
import { configureApplication } from './configure-application';

async function bootstrap(): Promise<void> {
  const app = await NestFactory.create<NestFastifyApplication>(AppModule, new FastifyAdapter({ logger: false }));
  const config = app.get(ConfigService);
  configureApplication(app);

  await app.listen(config.get<number>('app.port', 3000), '0.0.0.0');
}

void bootstrap();
