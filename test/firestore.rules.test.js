const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require('@firebase/rules-unit-testing');
const fs = require('fs');
const path = require('path');

let testEnv;

beforeAll(async () => {
  const rules = fs.readFileSync(path.resolve(__dirname, '../firestore.rules'), 'utf8');
  testEnv = await initializeTestEnvironment({
    projectId: 'easypark-test-project',
    firestore: { rules },
  });
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

afterAll(async () => {
  await testEnv.cleanup();
});

describe('EasyPark Firestore Security Rules', () => {

  // --- Users Collection Tests ---
  test('User can read their own profile', async () => {
    const client = testEnv.authenticatedContext('user1');
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().collection('users').doc('user1').set({
        fullName: 'User One', role: 'client', balance: 0.0, suspended: false
      });
    });

    const docRef = client.firestore().collection('users').doc('user1');
    await assertSucceeds(docRef.get());
  });

  test('User CANNOT read another user profile (unless staff)', async () => {
    const client = testEnv.authenticatedContext('user1');
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().collection('users').doc('user2').set({
        fullName: 'User Two', role: 'client', balance: 0.0, suspended: false
      });
    });

    const docRef = client.firestore().collection('users').doc('user2');
    await assertFails(docRef.get());
  });

  test('Staff (agent) CAN read all user profiles', async () => {
    const agent = testEnv.authenticatedContext('agent1');
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().collection('users').doc('agent1').set({
        role: 'agent', suspended: false
      });
      await context.firestore().collection('users').doc('user2').set({
        fullName: 'User Two', role: 'client', balance: 0.0, suspended: false
      });
    });

    const docRef = agent.firestore().collection('users').doc('user2');
    await assertSucceeds(docRef.get());
  });

  test('Client can create their own account with role == client and balance == 0', async () => {
    const client = testEnv.authenticatedContext('user1');
    const docRef = client.firestore().collection('users').doc('user1');
    await assertSucceeds(docRef.set({
      fullName: 'User One',
      role: 'client',
      balance: 0,
      suspended: false
    }));
  });

  test('Client CANNOT create account with role == admin', async () => {
    const client = testEnv.authenticatedContext('user1');
    const docRef = client.firestore().collection('users').doc('user1');
    await assertFails(docRef.set({
      fullName: 'Hacker',
      role: 'admin',
      balance: 0,
      suspended: false
    }));
  });

  test('Client CAN update fullName on their own profile', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().collection('users').doc('user1').set({
        fullName: 'Old Name', role: 'client', balance: 10.0, suspended: false
      });
    });

    const client = testEnv.authenticatedContext('user1');
    const docRef = client.firestore().collection('users').doc('user1');
    await assertSucceeds(docRef.update({
      fullName: 'New Name'
    }));
  });

  test('Client CANNOT update role or suspended flag on their profile', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().collection('users').doc('user1').set({
        fullName: 'User One', role: 'client', balance: 10.0, suspended: false
      });
    });

    const client = testEnv.authenticatedContext('user1');
    const docRef = client.firestore().collection('users').doc('user1');
    await assertFails(docRef.update({
      role: 'admin'
    }));
    await assertFails(docRef.update({
      suspended: true
    }));
  });

  // --- Spots Collection Tests ---
  test('Signed-in user can read spots', async () => {
    const client = testEnv.authenticatedContext('user1');
    const spotRef = client.firestore().collection('spots').doc('A1');
    await assertSucceeds(spotRef.get());
  });

  test('Client can reserve a free spot for themselves', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().collection('users').doc('user1').set({
        role: 'client', suspended: false
      });
      await context.firestore().collection('spots').doc('A1').set({
        status: 'libre', clientId: '', type: 'voiture'
      });
    });

    const client = testEnv.authenticatedContext('user1');
    const spotRef = client.firestore().collection('spots').doc('A1');
    await assertSucceeds(spotRef.update({
      status: 'reservee',
      clientId: 'user1'
    }));
  });

  test('Client CANNOT reserve a spot for another user', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().collection('users').doc('user1').set({
        role: 'client', suspended: false
      });
      await context.firestore().collection('spots').doc('A1').set({
        status: 'libre', clientId: '', type: 'voiture'
      });
    });

    const client = testEnv.authenticatedContext('user1');
    const spotRef = client.firestore().collection('spots').doc('A1');
    await assertFails(spotRef.update({
      status: 'reservee',
      clientId: 'user2' // mismatch
    }));
  });

  test('Staff CANNOT delete an occupied spot', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().collection('users').doc('agent1').set({
        role: 'agent', suspended: false
      });
      await context.firestore().collection('spots').doc('A1').set({
        status: 'occupee', clientId: 'user1', type: 'voiture'
      });
    });

    const agent = testEnv.authenticatedContext('agent1');
    const spotRef = agent.firestore().collection('spots').doc('A1');
    await assertFails(spotRef.delete());
  });

  test('Staff CAN delete a free spot', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().collection('users').doc('agent1').set({
        role: 'agent', suspended: false
      });
      await context.firestore().collection('spots').doc('A1').set({
        status: 'libre', clientId: '', type: 'voiture'
      });
    });

    const agent = testEnv.authenticatedContext('agent1');
    const spotRef = agent.firestore().collection('spots').doc('A1');
    await assertSucceeds(spotRef.delete());
  });

  // --- Tickets Collection Tests ---
  test('Tickets are immutable once created', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().collection('tickets').doc('t1').set({
        clientId: 'user1', amountMillimes: 2000
      });
    });

    const client = testEnv.authenticatedContext('user1');
    const ticketRef = client.firestore().collection('tickets').doc('t1');
    await assertFails(ticketRef.update({ amountMillimes: 0 }));
    await assertFails(ticketRef.delete());
  });

  // --- Config / Tarifs Collection Tests ---
  test('Client CANNOT write to config/tarif', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().collection('users').doc('user1').set({
        role: 'client', suspended: false
      });
    });

    const client = testEnv.authenticatedContext('user1');
    const configRef = client.firestore().collection('config').doc('tarif');
    await assertFails(configRef.set({ carHourlyRate: 0.0 }));
  });

  test('Admin CAN write to config/tarif', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().collection('users').doc('admin1').set({
        role: 'admin', suspended: false
      });
    });

    const admin = testEnv.authenticatedContext('admin1');
    const configRef = admin.firestore().collection('config').doc('tarif');
    await assertSucceeds(configRef.set({ carHourlyRate: 2.5, motoHourlyRate: 1.2 }));
  });

});
