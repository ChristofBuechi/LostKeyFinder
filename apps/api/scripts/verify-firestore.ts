import { randomUUID } from 'node:crypto';
import { MongoClient } from 'mongodb';

async function verify(): Promise<void> {
  const uri = process.env.FIRESTORE_MONGODB_URI;
  if (!uri) {
    throw new Error('FIRESTORE_MONGODB_URI is required for the Firestore integration test');
  }

  const client = new MongoClient(uri, { tls: true, loadBalanced: true, retryWrites: false });
  const database = client.db();
  const collection = database.collection<{ id: string; value: number }>('integrationProbes');
  const id = randomUUID();
  let connected = false;

  try {
    await client.connect();
    connected = true;
    await database.command({ ping: 1 });
    const session = client.startSession();
    try {
      await session.withTransaction(async () => {
        await collection.insertOne({ id, value: 1 }, { session });
        await collection.updateOne({ id }, { $set: { value: 2 } }, { session });
      });
    } finally {
      await session.endSession();
    }

    const probe = await collection.findOne({ id });
    if (probe?.value !== 2) {
      throw new Error('Firestore transaction verification failed');
    }
  } finally {
    if (connected) {
      await collection.deleteOne({ id });
    }
    await client.close();
  }
}

void verify();
