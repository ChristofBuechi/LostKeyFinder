import { Controller, Get } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { ApiOkResponse, ApiOperation, ApiTags } from '@nestjs/swagger';
import { VersionResponseDto } from './version-response.dto';

@ApiTags('version')
@Controller('version')
export class VersionController {
  constructor(private readonly config: ConfigService) {}

  @Get()
  @ApiOperation({ summary: 'Return the running API version' })
  @ApiOkResponse({ type: VersionResponseDto })
  version(): VersionResponseDto {
    return {
      version: this.config.get<string>('app.version', '0.1.0'),
      environment: this.config.get<string>('app.environment', 'development'),
    };
  }
}
