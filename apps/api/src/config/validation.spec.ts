import { validationSchema } from './validation';

describe('configuration validation', () => {
  it('allows local development defaults', () => {
    const result = validationSchema.validate({ NODE_ENV: 'development' });
    expect(result.error).toBeUndefined();
  });

  it('rejects production without infrastructure configuration', () => {
    const result = validationSchema.validate({ NODE_ENV: 'production' }, { abortEarly: false });
    expect(result.error?.message).toContain('FIRESTORE_MONGODB_URI');
    expect(result.error?.message).toContain('SUPABASE_URL');
  });

  it('requires GCP OIDC properties in the production Firestore URI', () => {
    const result = validationSchema.validate({
      NODE_ENV: 'production',
      ALLOWED_ORIGINS: 'https://example.com',
      FIRESTORE_MONGODB_URI: 'mongodb://example.com/database',
      SUPABASE_URL: 'https://example.supabase.co',
      SUPABASE_ANON_KEY: 'key',
      SUPABASE_JWT_AUDIENCE: 'authenticated',
    });
    expect(result.error?.message).toContain('FIRESTORE_MONGODB_URI');
  });
});
