import { Test } from '@nestjs/testing';
import { ServiceUnavailableException } from '@nestjs/common';
import { FirestoreService } from '../../infrastructure/firestore/firestore.service';
import { HealthController } from './health.controller';

describe('HealthController', () => {
  const firestore = { isReady: jest.fn().mockResolvedValue(true) };

  async function createController(): Promise<HealthController> {
    const module = await Test.createTestingModule({
      controllers: [HealthController],
      providers: [{ provide: FirestoreService, useValue: firestore }],
    }).compile();
    return module.get(HealthController);
  }

  it('reports a live process', async () => {
    expect((await createController()).live()).toEqual({ status: 'ok' });
  });

  it('reports readiness when Firestore responds', async () => {
    await expect((await createController()).ready()).resolves.toEqual({ status: 'ok' });
  });

  it('rejects readiness when Firestore is unavailable', async () => {
    firestore.isReady.mockResolvedValueOnce(false);
    await expect((await createController()).ready()).rejects.toBeInstanceOf(ServiceUnavailableException);
  });
});
