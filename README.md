# SecureLoginApp

SecureLoginApp is a full-stack authentication application with an Android client and a Python API. It supports account registration and login, email and mobile OTP verification, JWT-based authentication, and password reset flows.

## Features

- User registration and login
- Email OTP verification
- Mobile OTP verification
- Dual OTP authentication support
- JWT access and refresh tokens
- Forgot password and reset password flows
- MongoDB-backed user and OTP data storage
- FastAPI health and database status endpoints
- Android UI built with Kotlin, Material Components, View Binding, and Navigation

## Tech Stack

### Android application

- Kotlin
- Android SDK 36
- Minimum Android SDK 26
- Material Components
- AndroidX lifecycle and Navigation
- Retrofit 2
- OkHttp
- Coroutines

### Backend API

- Python
- FastAPI
- MongoDB / Motor
- PyMongo
- PyJWT
- Pydantic
- Uvicorn
- SMTP and HTTP-based email delivery

## Project Structure

```text
SecureLoginApp/
├── app/                    # Android application
│   └── src/main/
│       ├── java/com/example/secureloginapp/
│       └── res/
├── backend/                # FastAPI backend
│   ├── app/
│   │   ├── api/v1/         # Authentication routes
│   │   ├── core/           # Configuration and security helpers
│   │   ├── db/             # MongoDB connection
│   │   ├── schemas/        # Request and response models
│   │   └── services/       # Application services
│   ├── app/main.py         # FastAPI entry point
│   └── requirements.txt
├── build.gradle.kts
└── settings.gradle.kts
```

## Requirements

### Android

- Android Studio
- JDK 17
- Android SDK 26 or newer
- An emulator or physical Android device

### Backend

- Python 3.10 or newer
- MongoDB or MongoDB Atlas
- A virtual environment tool such as `venv`

## Backend Setup

1. Create and activate a Python virtual environment:

```bash
cd backend
python -m venv .venv
source .venv/bin/activate
```

On Windows, activate it with:

```powershell
.venv\Scripts\activate
```

2. Install dependencies:

```bash
pip install -r requirements.txt
```

3. Create a `.env` file in the `backend` directory:

```dotenv
ENVIRONMENT=development
PORT=8000
MONGODB_URL=mongodb://localhost:27017
DATABASE_NAME=login_system
JWT_SECRET_KEY=replace-with-a-long-random-secret
JWT_ALGORITHM=HS256
ACCESS_TOKEN_EXPIRE_MINUTES=30
REFRESH_TOKEN_EXPIRE_DAYS=7
OTP_EXPIRE_MINUTES=5
RESEND_COOLDOWN_SECONDS=60
MAX_VERIFICATION_ATTEMPTS=5
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USER=your-email@example.com
SMTP_PASSWORD=your-email-app-password
```

4. Start the API:

```bash
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

The API is available at `http://localhost:8000`. Interactive Swagger documentation is available at `http://localhost:8000/docs`.

## Android Setup

1. Open the repository root in Android Studio.
2. Let Gradle synchronize the project and download dependencies.
3. Update the backend base URL in the Android networking configuration if it differs from `localhost` or `10.0.2.2`.
4. Build and run the `app` module on an emulator or physical device.

When using the Android emulator, use `10.0.2.2` to access a backend running on your development machine.

## API Endpoints

The authentication routes are registered from `backend/app/api/v1/auth.py`.

- `GET /` — API status
- `GET /health` — database health check
- Authentication endpoints for registration, login, OTP verification, token operations, and password recovery are available under the application router

Open `/docs` for the generated request and response specifications.

## Security Notes

- Replace all development secrets before deploying.
- Store credentials in environment variables rather than committed source files.
- Use HTTPS for the API in production.
- Configure a restricted CORS policy for deployed environments.
- Use strong, unique JWT secrets and rotate secrets according to your security policy.

## Testing

Run Android unit tests:

```bash
./gradlew test
```

Run Android instrumentation tests:

```bash
./gradlew connectedAndroidTest
```

For the Python backend, add tests and run them with your preferred test runner, for example:

```bash
cd backend
pytest
```

## Contributing

Contributions are welcome. Please open an issue to discuss significant changes before creating a pull request.

## License

No license has been specified for this project.