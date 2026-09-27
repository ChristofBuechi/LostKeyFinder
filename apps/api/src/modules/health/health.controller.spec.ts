import { Test } from '@nestjs/testing';
import { ServiceUnavailableException } from '@nestjs/common';
import { FirestoreService } from '../../infrastructure/firestore/firestore.service';
import { FirestoreServiceFake } from '../../../test/support/firestore-service.fake';
import { HealthController } from './health.controller';

describe('HealthController', () => {
  const firestore = new FirestoreServiceFake();

  beforeEach(() => firestore.setReady(true));

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
    firestore.setReady(false);
    await expect((await createController()).ready()).rejects.toBeInstanceOf(ServiceUnavailableException);
  });
});
