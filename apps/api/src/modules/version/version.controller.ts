import { Controller, Get } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { ApiOperation, ApiTags } from '@nestjs/swagger';

@ApiTags('version')
@Controller('version')
export class VersionController {
  constructor(private readonly config: ConfigService) {}

  @Get()
  @ApiOperation({ summary: 'Return the running API version' })
  version() {
    return {
      version: this.config.get<string>('app.version', '0.1.0'),
      environment: this.config.get<string>('app.environment', 'development'),
    };
  }
}
