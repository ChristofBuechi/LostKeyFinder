import { validationSchema } from './validation';

describe('configuration validation', () => {
  it('allows local development defaults', () => {
    const result = validationSchema.validate({ NODE_ENV: 'development' });
    expect(result.error).toBeUndefined();
  });

  it('rejects production without infrastructure configuration', () => {
    const result = validationSchema.validate({ NODE_ENV: 'production' }, { abortEarly: false });
    expect(result.error?.message).toContain('FIRESTORE_MONGODB_URI');
  });

  it('requires GCP OIDC properties in the production Firestore URI', () => {
    const result = validationSchema.validate({
      NODE_ENV: 'production',
      ALLOWED_ORIGINS: 'https://example.com',
      FIRESTORE_MONGODB_URI: 'mongodb://example.com/database',
    });
    expect(result.error?.message).toContain('FIRESTORE_MONGODB_URI');
  });
});
