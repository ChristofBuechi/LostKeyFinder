import { ApiProperty } from '@nestjs/swagger';

export class VersionResponseDto {
  @ApiProperty({ type: String, example: '0.1.0' })
  version!: string;

  @ApiProperty({ type: String, example: 'development' })
  environment!: string;
}
