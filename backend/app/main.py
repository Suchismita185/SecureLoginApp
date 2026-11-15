from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.db.mongodb import connect_to_mongo, close_mongo_connection, db
from app.core.config import settings
from app.api.v1.auth import router as auth_router

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup logic: connect to MongoDB
    await connect_to_mongo()
    yield
    # Shutdown logic: close MongoDB connection
    await close_mongo_connection()

app = FastAPI(
    title=settings.APP_NAME,
    description="Dual OTP Authentication System with FastAPI and MongoDB Atlas",
    version="1.0.0",
    lifespan=lifespan
)

# Configure CORS for mobile app and client access
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include Authentication Router
app.include_router(auth_router)

@app.get("/", tags=["Health Check"])
async def root():
    return {
        "status": "online",
        "message": f"{settings.APP_NAME} is running smoothly",
        "version": "1.0.0"
    }

@app.get("/health", tags=["Health Check"])
async def health_check():
    try:
        await db.client.admin.command("ping")

        return {
            "status": "healthy",
            "database": "connected",
            "database_name": settings.DATABASE_NAME
        }

    except Exception:
        return {
            "status": "unhealthy",
            "database": "disconnected",
            "database_name": settings.DATABASE_NAME
        }