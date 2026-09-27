import { ArgumentsHost, BadRequestException, HttpException, Logger } from '@nestjs/common';
import { ProblemDetailsFilter } from './problem-details.filter';

function createHost(url = '/api/v1/items?token=secret') {
  const request = { method: 'GET', url, id: 'req-test' };
  const reply = {
    status: jest.fn().mockReturnThis(),
    type: jest.fn().mockReturnThis(),
    send: jest.fn(),
  };
  const host = {
    switchToHttp: () => ({
      getRequest: () => request,
      getResponse: () => reply,
    }),
  } as unknown as ArgumentsHost;

  return { host, reply };
}

describe('ProblemDetailsFilter', () => {
  afterEach(() => jest.restoreAllMocks());

  it('normalizes validation arrays and strips query parameters from instances', () => {
    const filter = new ProblemDetailsFilter();
    const { host, reply } = createHost();

    filter.catch(new BadRequestException(['name must be a string']), host);

    expect(reply.status).toHaveBeenCalledWith(400);
    expect(reply.type).toHaveBeenCalledWith('application/problem+json');
    expect(reply.send).toHaveBeenCalledWith({
      type: '/problems/invalid-request',
      title: 'Invalid request',
      status: 400,
      detail: 'Request validation failed',
      errors: ['name must be a string'],
      instance: '/api/v1/items',
      correlationId: 'req-test',
    });
  });

  it('does not expose internal details for unexpected errors', () => {
    const filter = new ProblemDetailsFilter();
    const { host, reply } = createHost();
    const logger = jest.spyOn(Logger.prototype, 'error').mockImplementation();

    filter.catch(new Error('database password=secret'), host);

    expect(reply.send).toHaveBeenCalledWith({
      type: '/problems/internal-server-error',
      title: 'Internal server error',
      status: 500,
      detail: undefined,
      errors: undefined,
      instance: '/api/v1/items',
      correlationId: 'req-test',
    });
    expect(JSON.stringify(reply.send.mock.calls[0][0])).not.toContain('secret');
    expect(logger).toHaveBeenCalledWith(expect.stringContaining('"path":"/api/v1/items"'));
    expect(logger.mock.calls[0][0]).not.toContain('database password');
  });

  it('maps known HTTP exceptions to stable problem types', () => {
    const filter = new ProblemDetailsFilter();
    const { host, reply } = createHost('/api/v1/items');

    filter.catch(new HttpException('Conflict detail', 409), host);

    expect(reply.send).toHaveBeenCalledWith(expect.objectContaining({
      type: '/problems/conflict',
      title: 'Conflict',
      status: 409,
      detail: 'Conflict detail',
    }));
  });

  it('uses a neutral problem type for unmapped client errors', () => {
    const filter = new ProblemDetailsFilter();
    const { host, reply } = createHost('/api/v1/items');

    filter.catch(new HttpException('Unprocessable item', 422), host);

    expect(reply.send).toHaveBeenCalledWith(expect.objectContaining({
      type: '/problems/request-failed',
      title: 'Request failed',
      status: 422,
      detail: 'Unprocessable item',
    }));
  });
});
