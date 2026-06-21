#!/bin/bash

# Run OpenAI Example Agent
# This script sets up environment and runs the OpenAI integration example

set -e

echo "╔════════════════════════════════════════════════════════════╗"
echo "║                                                            ║"
echo "║        OpenAI Agent Connect Example                       ║"
echo "║                                                            ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""

# Check if .env exists
if [ ! -f .env ]; then
    echo "❌ .env file not found!"
    echo ""
    echo "Create it from the example:"
    echo "  cp .env.example .env"
    echo "  nano .env  # Add your credentials"
    echo ""
    exit 1
fi

# Load environment variables
echo "📝 Loading environment variables from .env..."
export $(cat .env | grep -v '^#' | grep -v '^$' | xargs)

# Check required Twilio credentials
MISSING=false

if [ -z "$TWILIO_ACCOUNT_SID" ]; then
    echo "❌ TWILIO_ACCOUNT_SID not set"
    MISSING=true
fi

if [ -z "$TWILIO_AUTH_TOKEN" ]; then
    echo "❌ TWILIO_AUTH_TOKEN not set"
    MISSING=true
fi

if [ -z "$TWILIO_API_KEY" ]; then
    echo "❌ TWILIO_API_KEY not set"
    MISSING=true
fi

if [ -z "$TWILIO_API_SECRET" ]; then
    echo "❌ TWILIO_API_SECRET not set"
    MISSING=true
fi

if [ -z "$TWILIO_CONVERSATION_CONFIGURATION_ID" ]; then
    echo "❌ TWILIO_CONVERSATION_CONFIGURATION_ID not set"
    MISSING=true
fi

if [ -z "$TWILIO_PHONE_NUMBER" ]; then
    echo "❌ TWILIO_PHONE_NUMBER not set"
    MISSING=true
fi

# Check OpenAI API key
if [ -z "$OPENAI_API_KEY" ]; then
    echo "❌ OPENAI_API_KEY not set"
    echo ""
    echo "Get your OpenAI API key at: https://platform.openai.com/api-keys"
    echo "Then add to .env: OPENAI_API_KEY=sk-proj-..."
    echo ""
    MISSING=true
fi

if [ "$MISSING" = true ]; then
    echo ""
    echo "See SETUP_CREDENTIALS.md for detailed setup instructions."
    exit 1
fi

echo "✅ All required credentials found"
echo ""

# Build the project
echo "🔨 Building project..."
./gradlew clean compileJava -x test --console=plain 2>&1 | grep -E "BUILD|error" || true
echo ""

# Run the OpenAI example
echo "🚀 Starting OpenAI Agent..."
echo ""
echo "═══════════════════════════════════════════════════════════"
echo ""

# Use -PmainClass to specify which main class to run
./gradlew bootRun -PmainClass=com.twilio.agentconnect.examples.openai.OpenAIResponsesAgent --console=plain
