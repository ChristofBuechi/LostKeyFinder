import { Controller, Get, ServiceUnavailableException } from '@nestjs/common';
import { ApiOkResponse, ApiOperation, ApiServiceUnavailableResponse, ApiTags } from '@nestjs/swagger';
import { FirestoreService } from '../../infrastructure/firestore/firestore.service';
import { HealthResponseDto } from './health-response.dto';

@ApiTags('health')
@Controller('health')
export class HealthController {
  constructor(private readonly firestore: FirestoreService) {}

  @Get('live')
  @ApiOperation({ summary: 'Check whether the API process is alive' })
  @ApiOkResponse({ type: HealthResponseDto })
  live(): HealthResponseDto {
    return { status: 'ok' };
  }

  @Get('ready')
  @ApiOperation({ summary: 'Check whether the API is ready for traffic' })
  @ApiOkResponse({ type: HealthResponseDto })
  @ApiServiceUnavailableResponse({ description: 'Firestore is unavailable' })
  async ready(): Promise<HealthResponseDto> {
    if (!await this.firestore.isReady()) {
      throw new ServiceUnavailableException('A required dependency is unavailable');
    }
    return { status: 'ok' };
  }
}
