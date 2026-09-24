import { INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { FastifyAdapter, NestFastifyApplication } from '@nestjs/platform-fastify';
import request from 'supertest';
import { AppModule } from '../src/app.module';
import { configureApplication } from '../src/configure-application';
import { FirestoreService } from '../src/infrastructure/firestore/firestore.service';

describe('API foundation', () => {
  let app: INestApplication;

  beforeAll(async () => {
    const module = await Test.createTestingModule({ imports: [AppModule] })
      .overrideProvider(FirestoreService)
      .useValue({ isReady: jest.fn().mockResolvedValue(true) })
      .compile();
    app = module.createNestApplication<NestFastifyApplication>(new FastifyAdapter());
    configureApplication(app as NestFastifyApplication);
    await app.init();
    await (app.getHttpAdapter().getInstance() as { ready: () => Promise<void> }).ready();
  });

  afterAll(async () => app.close());

  it('serves the versioned readiness endpoint with a correlation id', async () => {
    const response = await request(app.getHttpServer())
      .get('/api/v1/health/ready')
      .expect(200);

    expect(response.body).toEqual({ status: 'ok' });
    expect(response.headers['x-correlation-id']).toMatch(/^[0-9a-f-]{36}$/);
  });

  it('does not expose an unversioned endpoint', async () => {
    await request(app.getHttpServer()).get('/health/live').expect(404);
  });

  it('replaces invalid correlation ids and removes query strings from problem instances', async () => {
    const response = await request(app.getHttpServer())
      .get('/api/v1/missing?code=secret')
      .set('x-correlation-id', 'untrusted-value')
      .expect(404);

    expect(response.body.instance).toBe('/api/v1/missing');
    expect(response.body.correlationId).not.toBe('untrusted-value');
    expect(response.headers['x-correlation-id']).toBe(response.body.correlationId);
  });
});
