import { Injectable, Logger, OnApplicationShutdown, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Db, MongoClient, MongoClientOptions } from 'mongodb';

@Injectable()
export class FirestoreService implements OnModuleInit, OnApplicationShutdown {
  private readonly logger = new Logger(FirestoreService.name);
  private client?: MongoClient;
  private database?: Db;

  constructor(private readonly config: ConfigService) {}

  async onModuleInit(): Promise<void> {
    const uri = this.config.get<string>('firestore.mongodbUri', '');
    if (!uri) {
      this.logger.warn('Firestore connection is not configured');
      return;
    }

    const options: MongoClientOptions = this.config.get('app.environment') === 'production'
      ? { tls: true, loadBalanced: true, retryWrites: false }
      : {};

    this.client = new MongoClient(uri, options);
    await this.client.connect();
    this.database = this.client.db(this.config.get<string>('firestore.databaseName', 'lost-key-finder'));
  }

  async isReady(): Promise<boolean> {
    if (!this.database) {
      return false;
    }

    try {
      await this.database.command({ ping: 1 });
      return true;
    } catch {
      return false;
    }
  }

  get db(): Db {
    if (!this.database) {
      throw new Error('Firestore connection is not ready');
    }
    return this.database;
  }

  async onApplicationShutdown(): Promise<void> {
    await this.client?.close();
  }
}
