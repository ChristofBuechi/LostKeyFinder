import Joi from 'joi';

export const validationSchema = Joi.object({
  NODE_ENV: Joi.string().valid('development', 'test', 'production').default('development'),
  PORT: Joi.number().integer().min(1).max(65535).default(3000),
  API_PREFIX: Joi.string().pattern(/^[a-z0-9-]+$/).default('api'),
  API_VERSION: Joi.string().pattern(/^v\d+$/).default('v1'),
  ALLOWED_ORIGINS: Joi.string().default('http://localhost:4200'),
  APP_VERSION: Joi.string().default('0.1.0'),
  FIRESTORE_MONGODB_URI: Joi.string().allow('').default(''),
  SUPABASE_URL: Joi.string().uri().allow('').default(''),
  SUPABASE_ANON_KEY: Joi.string().allow('').default(''),
  SUPABASE_JWT_AUDIENCE: Joi.string().allow('').default(''),
  SUPABASE_JWT_ALGORITHM: Joi.string().valid('ES256', 'RS256').default('ES256'),
});
