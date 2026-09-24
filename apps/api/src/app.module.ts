import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { validationSchema } from './config/validation';
import { HealthModule } from './modules/health/health.module';
import { VersionModule } from './modules/version/version.module';

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      cache: true,
      validationSchema,
      validationOptions: { abortEarly: false, allowUnknown: true },
    }),
    HealthModule,
    VersionModule,
  ],
})
export class AppModule {}
