import Joi from 'joi';

const requiredInProduction = Joi.string().when('NODE_ENV', {
  is: 'production',
  then: Joi.string().required(),
  otherwise: Joi.string().allow('').default(''),
});

export const validationSchema = Joi.object({
  NODE_ENV: Joi.string().valid('development', 'test', 'production').default('development'),
  PORT: Joi.number().integer().min(1).max(65535).default(3000),
  API_PREFIX: Joi.string().pattern(/^[a-z0-9-]+$/).default('api'),
  API_VERSION: Joi.string().pattern(/^v\d+$/).default('v1'),
  ALLOWED_ORIGINS: Joi.string()
    .custom((value: string, helpers) => {
      const origins = value.split(',').map((origin) => origin.trim());
      if (origins.some((origin) => {
        try {
          const url = new URL(origin);
          return !['http:', 'https:'].includes(url.protocol) || url.origin !== origin;
        } catch {
          return true;
        }
      })) {
        return helpers.error('string.uri');
      }
      return value;
    })
    .when('NODE_ENV', {
      is: 'production',
      then: Joi.string().required(),
      otherwise: Joi.string().default('http://localhost:4200'),
    }),
  APP_VERSION: Joi.string().default('0.1.0'),
  FIRESTORE_MONGODB_URI: Joi.string()
    .custom((value: string, helpers) => {
      if (!/^mongodb(\+srv)?:\/\//.test(value)) {
        return helpers.error('string.uri');
      }
      const environment = helpers.state.ancestors[0]?.NODE_ENV;
      const decodedUri = decodeURIComponent(value);
      if (environment === 'production'
        && (!decodedUri.includes('authMechanism=MONGODB-OIDC')
          || !decodedUri.includes('ENVIRONMENT:gcp')
          || !decodedUri.includes('TOKEN_RESOURCE:FIRESTORE'))) {
        return helpers.error('any.invalid');
      }
      return value;
    })
    .when('NODE_ENV', {
      is: 'production',
      then: Joi.string().required(),
      otherwise: Joi.string().allow('').default(''),
    }),
  FIRESTORE_DATABASE_NAME: Joi.string().min(1).default('lost-key-finder'),
  SUPABASE_URL: Joi.string().uri().when('NODE_ENV', {
    is: 'production',
    then: Joi.string().required(),
    otherwise: Joi.string().allow('').default(''),
  }),
  SUPABASE_ANON_KEY: requiredInProduction,
  SUPABASE_JWT_AUDIENCE: requiredInProduction,
  SUPABASE_JWT_ALGORITHM: Joi.string().valid('ES256', 'RS256').default('ES256'),
});
