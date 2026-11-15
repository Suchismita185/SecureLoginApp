
import logging
from zoneinfo import ZoneInfo

from motor.motor_asyncio import AsyncIOMotorClient
from app.core.config import settings

logger = logging.getLogger(__name__)

# Indian Standard Time
IST = ZoneInfo("Asia/Kolkata")


class Database:
    client: AsyncIOMotorClient = None
    db = None


db = Database()


async def connect_to_mongo():
    logger.info("Connecting to MongoDB...")

    db.client = AsyncIOMotorClient(
        settings.MONGODB_URL,
        tz_aware=True,
        tzinfo=IST
    )

    db.db = db.client[settings.DATABASE_NAME]

    # Ensure indexes on startup
    await init_db_indexes()

    logger.info(
        f"Connected to MongoDB database: {settings.DATABASE_NAME}"
    )


async def close_mongo_connection():
    logger.info("Closing MongoDB connection...")

    if db.client:
        db.client.close()
        logger.info("MongoDB connection closed.")


async def init_db_indexes():
    """Create unique indexes for collections to enforce constraints."""

    if db.db is None:
        return

    try:
        # Users collection indexes
        await db.db.users.create_index("email", unique=True)
        await db.db.users.create_index("phone_number", unique=True)

        # Pending registrations indexes
        await db.db.pending_registrations.create_index(
            "email", unique=True
        )
        await db.db.pending_registrations.create_index(
            "phone_number", unique=True
        )
        await db.db.pending_registrations.create_index(
            "created_at",
            expireAfterSeconds=86400
        )

        # OTP verification indexes
        await db.db.otp_verifications.create_index(
            "registration_ref", unique=True
        )

        # Refresh session indexes
        await db.db.refresh_sessions.create_index("user_id")
        await db.db.refresh_sessions.create_index(
            "refresh_token_hash", unique=True
        )

        logger.info("Database indexes created successfully.")

    except Exception as e:
        logger.warning(f"Note on index creation: {e}")
