import { Controller, Get } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { ApiOkResponse, ApiOperation, ApiProperty, ApiTags } from '@nestjs/swagger';

class VersionResponseDto {
  @ApiProperty({ type: String, example: '0.1.0' })
  version!: string;

  @ApiProperty({ type: String, example: 'development' })
  environment!: string;
}

@ApiTags('version')
@Controller('version')
export class VersionController {
  constructor(private readonly config: ConfigService) {}

  @Get()
  @ApiOperation({ summary: 'Return the running API version' })
  @ApiOkResponse({ type: VersionResponseDto })
  version(): VersionResponseDto {
    return {
      version: this.config.get<string>('APP_VERSION', '0.1.0'),
      environment: this.config.get<string>('NODE_ENV', 'development'),
    };
  }
}
