import { ApiProperty } from '@nestjs/swagger';

export class HealthResponseDto {
  @ApiProperty({ type: String, example: 'ok', enum: ['ok'] })
  status!: string;
}
