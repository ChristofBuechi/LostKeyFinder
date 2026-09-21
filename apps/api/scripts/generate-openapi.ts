import { writeFile } from 'node:fs/promises';
import { NestFactory } from '@nestjs/core';
import { FastifyAdapter, NestFastifyApplication } from '@nestjs/platform-fastify';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';
import { AppModule } from '../src/app.module';

async function generate(): Promise<void> {
  const app = await NestFactory.create<NestFastifyApplication>(AppModule, new FastifyAdapter({ logger: false }));
  const document = SwaggerModule.createDocument(app, new DocumentBuilder().setTitle('Lost Key Finder API').setVersion('0.1.0').build());
  await writeFile('openapi.json', `${JSON.stringify(document, null, 2)}\n`);
  await app.close();
}

void generate();
