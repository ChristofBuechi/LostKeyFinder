import { Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { MongoClient } from 'mongodb';
import { FirestoreService } from './firestore.service';

jest.mock('mongodb', () => ({ MongoClient: jest.fn() }));

type FakeClient = {
  connect: jest.Mock;
  db: jest.Mock;
  close: jest.Mock;
};

describe('FirestoreService', () => {
  let client: FakeClient;
  let database: { command: jest.Mock };
  let config: ConfigService;

  beforeEach(() => {
    jest.spyOn(Logger.prototype, 'warn').mockImplementation();
    database = { command: jest.fn().mockResolvedValue({ ok: 1 }) };
    client = {
      connect: jest.fn().mockResolvedValue(undefined),
      db: jest.fn().mockReturnValue(database),
      close: jest.fn().mockResolvedValue(undefined),
    };
    (MongoClient as unknown as jest.Mock).mockReset().mockImplementation(() => client);
    config = {
      get: jest.fn((key: string, fallback?: unknown) => {
        if (key === 'FIRESTORE_MONGODB_URI') return fallback;
        return key === 'NODE_ENV' ? 'development' : fallback;
      }),
    } as unknown as ConfigService;
  });

  afterEach(() => jest.restoreAllMocks());

  it('stays unavailable without a configured URI', async () => {
    const service = new FirestoreService(config);

    await service.onModuleInit();

    expect(MongoClient).not.toHaveBeenCalled();
    await expect(service.isReady()).resolves.toBe(false);
  });

  it('connects once and reports readiness after a successful ping', async () => {
    config.get = jest.fn((key: string, fallback?: unknown) => {
      if (key === 'FIRESTORE_MONGODB_URI') return 'mongodb://localhost/test';
      return key === 'NODE_ENV' ? 'development' : fallback;
    }) as ConfigService['get'];
    const service = new FirestoreService(config);

    await service.onModuleInit();

    expect(MongoClient).toHaveBeenCalledWith('mongodb://localhost/test', {});
    expect(client.connect).toHaveBeenCalledTimes(1);
    await expect(service.isReady()).resolves.toBe(true);
    expect(database.command).toHaveBeenCalledWith({ ping: 1 });
  });

  it('reports unavailable when the readiness ping fails', async () => {
    config.get = jest.fn((key: string, fallback?: unknown) => {
      if (key === 'FIRESTORE_MONGODB_URI') return 'mongodb://localhost/test';
      return key === 'NODE_ENV' ? 'development' : fallback;
    }) as ConfigService['get'];
    database.command.mockRejectedValueOnce(new Error('connection failed'));
    const service = new FirestoreService(config);

    await service.onModuleInit();

    await expect(service.isReady()).resolves.toBe(false);
  });

  it('uses production transport options and closes the client', async () => {
    config.get = jest.fn((key: string, fallback?: unknown) => {
      if (key === 'FIRESTORE_MONGODB_URI') return 'mongodb://localhost/test';
      return key === 'NODE_ENV' ? 'production' : fallback;
    }) as ConfigService['get'];
    const service = new FirestoreService(config);

    await service.onModuleInit();
    await service.onApplicationShutdown();

    expect(MongoClient).toHaveBeenCalledWith('mongodb://localhost/test', {
      tls: true,
      loadBalanced: true,
      retryWrites: false,
    });
    expect(client.close).toHaveBeenCalledTimes(1);
  });

  it('allows shutdown before initialization', async () => {
    const service = new FirestoreService(config);

    await expect(service.onApplicationShutdown()).resolves.toBeUndefined();
  });
});
