import { validationSchema } from './validation';

describe('configuration validation', () => {
  it('allows local development defaults', () => {
    const result = validationSchema.validate({ NODE_ENV: 'development' });
    expect(result.error).toBeUndefined();
    expect(result.value).toMatchObject({
      PORT: 3000,
      ALLOWED_ORIGINS: 'http://localhost:4200',
      APP_VERSION: '0.1.0',
      FIRESTORE_MONGODB_URI: '',
    });
  });

  it('rejects unknown environments and invalid ports', () => {
    expect(validationSchema.validate({ NODE_ENV: 'preview' }).error).toBeDefined();
    expect(validationSchema.validate({ PORT: 0 }).error).toBeDefined();
    expect(validationSchema.validate({ PORT: 65536 }).error).toBeDefined();
  });

  it('rejects origins with paths, queries, or unsupported protocols', () => {
    for (const origin of [
      'https://example.com/path',
      'https://example.com?token=secret',
      'ftp://example.com',
      'https://example.com/',
    ]) {
      expect(validationSchema.validate({ ALLOWED_ORIGINS: origin }).error).toBeDefined();
    }
  });

  it('accepts multiple trimmed browser origins', () => {
    const result = validationSchema.validate({
      ALLOWED_ORIGINS: 'https://one.example, https://two.example',
    });

    expect(result.error).toBeUndefined();
    expect(result.value.ALLOWED_ORIGINS).toBe('https://one.example,https://two.example');
  });

  it('rejects production without infrastructure configuration', () => {
    const result = validationSchema.validate({ NODE_ENV: 'production' }, { abortEarly: false });
    expect(result.error?.message).toContain('FIRESTORE_MONGODB_URI');
  });

  it('requires structured GCP OIDC properties in the production Firestore URI', () => {
    for (const uri of [
      'mongodb://example.com/database',
      'mongodb://example.com/database?authMechanism=MONGODB-OIDC&ENVIRONMENT%3Agcp&TOKEN_RESOURCE%3AFIRESTORE',
      'mongodb://example.com/database?note=authMechanism%3DMONGODB-OIDC%20ENVIRONMENT%3Agcp%20TOKEN_RESOURCE%3AFIRESTORE',
    ]) {
      const result = validationSchema.validate({
        NODE_ENV: 'production',
        ALLOWED_ORIGINS: 'https://example.com',
        FIRESTORE_MONGODB_URI: uri,
      });
      expect(result.error?.message).toContain('FIRESTORE_MONGODB_URI');
    }
  });

  it('accepts a production Firestore URI with the required OIDC properties', () => {
    const result = validationSchema.validate({
      NODE_ENV: 'production',
      ALLOWED_ORIGINS: 'https://example.com',
      FIRESTORE_MONGODB_URI: 'mongodb://example.com/database?authMechanism=MONGODB-OIDC&authMechanismProperties=ENVIRONMENT%3Agcp%2CTOKEN_RESOURCE%3AFIRESTORE',
    });

    expect(result.error).toBeUndefined();
  });
});
