import { Module } from '@nestjs/common';
import { FirestoreService } from '../../infrastructure/firestore/firestore.service';
import { HealthController } from './health.controller';

@Module({
  controllers: [HealthController],
  providers: [FirestoreService],
})
export class HealthModule {}
