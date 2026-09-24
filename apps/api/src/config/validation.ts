import Joi from 'joi';

export const validationSchema = Joi.object({
  NODE_ENV: Joi.string().valid('development', 'test', 'production').default('development'),
  PORT: Joi.number().integer().min(1).max(65535).default(3000),
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
});
