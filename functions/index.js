const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

exports.releaseExpiredReservations = functions.pubsub.schedule("every 1 minutes").onRun(async (context) => {
    const db = admin.firestore();
    const MAX_RESERVATION_MINUTES = 15;
    const maxDurationMillis = MAX_RESERVATION_MINUTES * 60 * 1000;
    const now = Date.now();
    const expiryThreshold = now - maxDurationMillis;

    const spotsSnapshot = await db.collection("spots")
        .where("status", "==", "reservee")
        .where("reservedAt", "<", expiryThreshold)
        .get();

    if (spotsSnapshot.empty) {
        console.log("No expired reservations found.");
        return null;
    }

    const batch = db.batch();
    let count = 0;

    spotsSnapshot.forEach(doc => {
        batch.update(doc.ref, {
            status: "libre",
            clientId: "",
            clientPlate: "",
            reservedAt: 0,
            startTime: 0
        });
        count++;
    });

    await batch.commit();
    console.log(`Successfully released ${count} expired reservations.`);
    return null;
});
