import { Test } from '@nestjs/testing';
import { HealthController } from './health.controller';

describe('HealthController', () => {
  it('reports a live process', async () => {
    const module = await Test.createTestingModule({
      controllers: [HealthController],
    }).compile();

    expect(module.get(HealthController).live()).toEqual({ status: 'ok' });
  });

  it('reports readiness', async () => {
    const module = await Test.createTestingModule({
      controllers: [HealthController],
    }).compile();

    expect(module.get(HealthController).ready()).toEqual({ status: 'ok' });
  });
});
